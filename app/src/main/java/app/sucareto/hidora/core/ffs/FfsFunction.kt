package app.sucareto.hidora.core.ffs

import android.system.ErrnoException
import android.system.OsConstants
import app.sucareto.hidora.core.gadget.EndpointIo
import app.sucareto.hidora.util.AppLog

class FfsSetupRequest(val requestType: Int, val request: Int, val value: Int, val index: Int, val length: Int) {
    val isDeviceToHost: Boolean get() = requestType and DIR_IN != 0

    companion object {
        private const val DIR_IN = 0x80
        const val TYPE_CLASS_INTERFACE_IN = 0xA1
        const val TYPE_VENDOR_DEVICE_IN = 0xC0
        const val TYPE_VENDOR_INTERFACE_IN = 0xC1
        const val TYPE_VENDOR_INTERFACE_OUT = 0x41
    }
}

sealed interface FfsSetupReply {
    class Data(val bytes: ByteArray) : FfsSetupReply
    data object Ack : FfsSetupReply
    data object Stall : FfsSetupReply
}

enum class FfsEvent { BIND, UNBIND, ENABLE, DISABLE, SETUP, SUSPEND, RESUME }

class FfsEndpoint internal constructor(val name: String, private val io: EndpointIo, handle: Long) {
    @Volatile private var handle: Long? = handle

    sealed interface IoResult {
        class Done(val count: Int) : IoResult
        data object NotEnabled : IoResult
        class Failed(val error: Throwable) : IoResult
    }

    fun write(data: ByteArray): IoResult = io { io.write(it, data) }

    fun read(buffer: ByteArray): IoResult = io { io.read(it, buffer) }

    private inline fun io(block: (Long) -> Int): IoResult {
        val current = handle ?: return IoResult.Failed(IllegalStateException("$name closed"))
        val result = block(current)
        return when {
            result >= 0 -> IoResult.Done(result)
            -result in NOT_ENABLED_ERRNOS -> IoResult.NotEnabled
            else -> IoResult.Failed(ErrnoException(name, -result))
        }
    }

    internal fun close() {
        val current = handle ?: return
        handle = null
        io.close(current)
    }

    private companion object {
        const val ERRNO_SHUTDOWN = 108
        val NOT_ENABLED_ERRNOS = setOf(OsConstants.EAGAIN, OsConstants.ENODEV, ERRNO_SHUTDOWN)
    }
}

class FfsFunction private constructor(
    val name: String,
    private val io: EndpointIo,
    private val ep0: Long,
    val endpoints: List<FfsEndpoint>
) {
    @Volatile var setupHandler: (FfsSetupRequest) -> FfsSetupReply = { FfsSetupReply.Stall }
    @Volatile var eventListener: (FfsEvent) -> Unit = {}
    @Volatile var isEnabled = false
        private set
    @Volatile private var closed = false
    private val eventThread = Thread(::eventLoop, "ffs-$name-ep0")

    private fun eventLoop() {
        val event = ByteArray(EVENT_SIZE)
        while (!closed) {
            val revents = io.poll(ep0, POLL_TIMEOUT_MS)
            if (revents == 0) continue
            val count = if (revents > 0) io.read(ep0, event) else revents
            if (count < 0) {
                val errno = -count
                if (errno == OsConstants.EBADF || errno == OsConstants.ENOTCONN) break
                if (errno == OsConstants.EAGAIN) Thread.sleep(SPIN_GUARD_MS)
                if (errno == OsConstants.EAGAIN || errno == OsConstants.EINTR) continue
                if (!closed) AppLog.e(TAG, "$name ep0 read failed", ErrnoException("ep0", errno))
                Thread.sleep(ERROR_RETRY_MS)
                continue
            }
            if (count < EVENT_SIZE) {
                Thread.sleep(SPIN_GUARD_MS)
                continue
            }
            val type = FfsEvent.entries.getOrNull(event[8].toInt() and 0xFF) ?: continue
            when (type) {
                FfsEvent.ENABLE -> isEnabled = true
                FfsEvent.DISABLE, FfsEvent.UNBIND -> isEnabled = false
                FfsEvent.SETUP -> answerSetup(event)
                else -> Unit
            }
            AppLog.i(TAG) { "$name event $type" }
            runCatching { eventListener(type) }.onFailure { AppLog.e(TAG, "$name listener failed", it) }
        }
    }

    private fun answerSetup(event: ByteArray) {
        val request = FfsSetupRequest(
            requestType = event[0].toInt() and 0xFF,
            request = event[1].toInt() and 0xFF,
            value = u16(event, 2),
            index = u16(event, 4),
            length = u16(event, 6)
        )
        val reply = runCatching { setupHandler(request) }.getOrDefault(FfsSetupReply.Stall)
        val result = when {
            reply is FfsSetupReply.Data && request.isDeviceToHost ->
                io.write(ep0, reply.bytes.copyOf(minOf(reply.bytes.size, request.length)))
            reply is FfsSetupReply.Ack && !request.isDeviceToHost -> io.read(ep0, ByteArray(request.length))
            request.isDeviceToHost -> io.read(ep0, ByteArray(0))
            else -> io.write(ep0, ByteArray(0))
        }
        if (result < 0 && -result != ERRNO_STALLED) AppLog.w(TAG, "$name setup reply failed", ErrnoException("ep0", -result))
        AppLog.i(TAG) {
            "$name setup %02x %02x %04x %04x %d -> %s".format(
                request.requestType, request.request, request.value, request.index, request.length, reply::class.simpleName)
        }
    }

    fun close() {
        closed = true
        io.close(ep0)
        runCatching { eventThread.join(JOIN_TIMEOUT_MS) }
        endpoints.forEach { it.close() }
    }

    companion object {
        private const val TAG = "FfsFunction"
        private const val EVENT_SIZE = 12
        private const val ERRNO_STALLED = 51
        private const val SPIN_GUARD_MS = 10L
        private const val POLL_TIMEOUT_MS = 1000
        private const val ERROR_RETRY_MS = 100L
        private const val JOIN_TIMEOUT_MS = 500L

        fun remote(name: String, io: EndpointIo, handles: LongArray): FfsFunction {
            require(handles.isNotEmpty()) { "no ep0 handle for $name" }
            val endpoints = handles.drop(1).mapIndexed { i, h -> FfsEndpoint("$name/ep${i + 1}", io, h) }
            return FfsFunction(name, io, handles[0], endpoints).also { it.eventThread.start() }
        }

        private fun u16(b: ByteArray, offset: Int) = (b[offset].toInt() and 0xFF) or ((b[offset + 1].toInt() and 0xFF) shl 8)
    }
}

class FfsFunctions(private val functions: Map<String, FfsFunction>) {
    operator fun get(name: String): FfsFunction = requireNotNull(functions[name]) { "no ffs function $name" }

    fun closeAll() = functions.values.forEach { it.close() }

    companion object {
        val EMPTY = FfsFunctions(emptyMap())
    }
}
