package app.sucareto.hidora.core.hid

import android.system.ErrnoException
import android.system.OsConstants
import app.sucareto.hidora.core.gadget.EndpointIo
import app.sucareto.hidora.util.AppLog

class HidEndpoint private constructor(
    val functionName: String,
    private val io: EndpointIo?,
    private var handle: Long?
) {
    private val lock = Any()

    private val isOpen: Boolean get() = synchronized(lock) { handle != null }

    fun write(report: ByteArray): Result<Unit> {
        val io = io ?: return Result.success(Unit)
        return synchronized(lock) {
            runCatching {
                val current = handle ?: error("$functionName: endpoint closed")
                val result = io.write(current, report)
                if (result < 0) throw ErrnoException("write", -result)
            }
        }.onFailure { if (!it.isExpected(OsConstants.EAGAIN)) AppLog.e(TAG, "$functionName write failed", it) }
    }

    fun awaitReadable(timeoutMs: Int): Result<Boolean> = runCatching {
        if (io == null) {
            Thread.sleep(timeoutMs.toLong())
            return@runCatching false
        }
        val current = synchronized(lock) { handle } ?: error("$functionName: endpoint closed")
        val revents = io.poll(current, timeoutMs)
        if (revents < 0) throw ErrnoException("poll", -revents)
        if ((revents and (OsConstants.POLLERR or OsConstants.POLLHUP or OsConstants.POLLNVAL)) != 0) {
            error("$functionName: poll revents=0x${revents.toString(16)}")
        }
        (revents and OsConstants.POLLIN) != 0
    }.onFailure { if (isOpen && !it.isExpected()) AppLog.e(TAG, "$functionName awaitReadable failed", it) }

    fun read(buffer: ByteArray): Result<Int> {
        val io = io ?: return Result.success(0)
        return synchronized(lock) {
            runCatching {
                val current = handle ?: error("$functionName: endpoint closed")
                val result = io.read(current, buffer)
                when {
                    result >= 0 -> result
                    -result == OsConstants.EAGAIN -> 0
                    else -> throw ErrnoException("read", -result)
                }
            }
        }.onFailure { if (isOpen && !it.isExpected()) AppLog.e(TAG, "$functionName read failed", it) }
    }

    fun close() = synchronized(lock) {
        val current = handle ?: return@synchronized
        handle = null
        io?.close(current)
    }

    private fun Throwable.isExpected(vararg errnos: Int) =
        (this as? ErrnoException)?.errno?.let { it == OsConstants.ENOTCONN || it in errnos } == true

    companion object {
        private const val TAG = "HidEndpoint"

        fun preview(functionName: String): HidEndpoint = HidEndpoint(functionName, null, null)

        fun remote(functionName: String, io: EndpointIo, handle: Long): HidEndpoint = HidEndpoint(functionName, io, handle)
    }
}

class HidEndpoints(private val endpoints: Map<String, HidEndpoint>) {
    operator fun get(functionName: String): HidEndpoint =
        requireNotNull(endpoints[functionName]) { "no endpoint for function $functionName" }

    fun closeAll() = endpoints.values.forEach { it.close() }

    companion object {
        val EMPTY = HidEndpoints(emptyMap())

        fun preview(functionNames: List<String>): HidEndpoints =
            HidEndpoints(functionNames.associateWith { HidEndpoint.preview(it) })

        fun remote(io: EndpointIo, handles: Map<String, Long>): HidEndpoints =
            HidEndpoints(handles.mapValues { (name, handle) -> HidEndpoint.remote(name, io, handle) })
    }
}
