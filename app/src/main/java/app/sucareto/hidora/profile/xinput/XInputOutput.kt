package app.sucareto.hidora.profile.xinput

import androidx.compose.ui.geometry.Offset
import app.sucareto.hidora.core.ffs.FfsEndpoint
import app.sucareto.hidora.core.ffs.FfsEvent
import app.sucareto.hidora.core.ffs.FfsFunction
import app.sucareto.hidora.core.ffs.FfsSetupReply
import app.sucareto.hidora.core.ffs.FfsSetupRequest
import app.sucareto.hidora.profile.gamepad.GamepadState
import app.sucareto.hidora.util.AppLog
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class XInputOutput(private val function: FfsFunction, private val deviceId: ByteArray, private val rumble: Rumble) : PadOutput {
    private val lock = Any()
    private val state = GamepadState()
    private val queue = LinkedBlockingDeque<Any>(PadIo.QUEUE_CAPACITY)
    private val flushPending = AtomicBoolean(false)
    @Volatile private var closed = false
    private val input = function.endpoints[0]
    private val output = function.endpoints[1]

    init {
        function.setupHandler = ::answerSetup
        function.eventListener = { event ->
            when (event) {
                FfsEvent.ENABLE -> requestFlush()
                FfsEvent.DISABLE, FfsEvent.UNBIND, FfsEvent.SUSPEND -> rumble.stop()
                else -> Unit
            }
        }
        Thread(::writeLoop, "xusb-in").start()
        Thread(::readLoop, "xusb-out").start()
    }

    override fun setButton(bit: Int, pressed: Boolean) = edge {
        buttons = if (pressed) buttons or bit else buttons and bit.inv()
    }

    override fun setHat(hat: Int) = edge { this.hat = hat }

    override fun setTrigger(left: Boolean, value: Int) {
        val crossed = synchronized(lock) {
            val previous = if (left) state.leftTrigger else state.rightTrigger
            if (left) state.leftTrigger = value else state.rightTrigger = value
            (previous == 0) != (value == 0)
        }
        if (crossed) enqueue(snapshot()) else requestFlush()
    }

    override fun setStick(left: Boolean, position: Offset) {
        synchronized(lock) {
            if (left) {
                state.leftX = position.x
                state.leftY = position.y
            } else {
                state.rightX = position.x
                state.rightY = position.y
            }
        }
        requestFlush()
    }

    override fun releaseAll() {
        synchronized(lock) { state.reset() }
        enqueue(snapshot())
        rumble.stop()
    }

    override fun close() {
        closed = true
        queue.clear()
        queue.offerFirst(CLOSE)
        function.eventListener = {}
        rumble.stop()
    }

    private fun answerSetup(request: FfsSetupRequest): FfsSetupReply = when {
        request.request == XusbReport.REQ_GET_REPORT &&
            (request.requestType == FfsSetupRequest.TYPE_CLASS_INTERFACE_IN || request.requestType == FfsSetupRequest.TYPE_VENDOR_INTERFACE_IN) ->
            FfsSetupReply.Data(snapshot())
        request.request == XusbReport.REQ_GET_DEVICE_ID && request.requestType == FfsSetupRequest.TYPE_VENDOR_DEVICE_IN -> FfsSetupReply.Data(deviceId)
        request.request == XusbReport.REQ_SET_CONTROL && request.requestType == FfsSetupRequest.TYPE_VENDOR_INTERFACE_OUT -> FfsSetupReply.Ack
        else -> FfsSetupReply.Stall
    }

    private fun edge(block: GamepadState.() -> Unit) {
        synchronized(lock) { state.block() }
        enqueue(snapshot())
    }

    private fun snapshot(): ByteArray = synchronized(lock) { XusbReport.build(state) }

    private fun requestFlush() {
        if (flushPending.compareAndSet(false, true)) enqueue(FLUSH)
    }

    private fun enqueue(item: Any) {
        while (!queue.offerLast(item)) {
            if (queue.pollFirst() === FLUSH) flushPending.set(false)
        }
    }

    private fun writeLoop() {
        while (!closed) {
            val item = queue.pollFirst(PadIo.IDLE_POLL_MS, TimeUnit.MILLISECONDS) ?: continue
            if (item === CLOSE) break
            val report = if (item === FLUSH) {
                flushPending.set(false)
                snapshot()
            } else {
                item as ByteArray
            }
            val result = input.write(report)
            if (result is FfsEndpoint.IoResult.Failed && !closed) {
                AppLog.w(TAG, "input write failed", result.error)
                Thread.sleep(PadIo.RETRY_DELAY_MS)
            }
        }
    }

    private fun readLoop() {
        val buffer = ByteArray(OUT_BUFFER_SIZE)
        while (!closed) {
            when (val result = output.read(buffer)) {
                is FfsEndpoint.IoResult.Done -> XusbReport.rumble(buffer, result.count)?.let { (strong, weak) ->
                    if (!closed) rumble.set(strong, weak)
                }
                is FfsEndpoint.IoResult.NotEnabled -> Thread.sleep(PadIo.NOT_ENABLED_RETRY_MS)
                else -> Thread.sleep(PadIo.RETRY_DELAY_MS)
            }
        }
    }

    private companion object {
        const val TAG = "XInputOutput"
        val FLUSH = Any()
        val CLOSE = Any()
        const val OUT_BUFFER_SIZE = 64
    }
}
