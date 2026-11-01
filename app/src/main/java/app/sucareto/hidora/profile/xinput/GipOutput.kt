package app.sucareto.hidora.profile.xinput

import android.os.SystemClock
import androidx.compose.ui.geometry.Offset
import app.sucareto.hidora.core.ffs.FfsEndpoint
import app.sucareto.hidora.core.ffs.FfsEvent
import app.sucareto.hidora.core.ffs.FfsFunction
import app.sucareto.hidora.profile.gamepad.GamepadReport
import app.sucareto.hidora.profile.gamepad.GamepadState
import app.sucareto.hidora.util.AppLog
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class GipOutput(
    private val function: FfsFunction,
    private val deviceId: ByteArray,
    private val vendorId: Int,
    private val productId: Int,
    private val rumble: Rumble
) : PadOutput {
    private enum class Phase { IDLE, ARRIVAL, METADATA, STOPPED, ACTIVE }

    private sealed interface Item
    private data object Enable : Item
    private data object Disable : Item
    private data object MetadataRequest : Item
    private class MetadataAck(val received: Int) : Item
    private class SetState(val state: Int) : Item
    private class Motor(val motor: GipReport.Motor) : Item
    private class Raw(val packet: ByteArray) : Item
    private class Input(val payload: ByteArray) : Item
    private class Guide(val pressed: Boolean) : Item
    private data object Flush : Item
    private data object Close : Item

    private val lock = Any()
    private val state = GamepadState()
    private val queue = LinkedBlockingDeque<Item>(PadIo.QUEUE_CAPACITY)
    private val flushPending = AtomicBoolean(false)
    @Volatile private var closed = false
    private val input = function.endpoints[0]
    private val output = function.endpoints[1]

    private var phase = Phase.IDLE
    private var systemSequence = 0
    private var inputSequence = 0
    private var metadataSequence = 0
    private var nextHelloAt = 0L
    private var nextStatusAt = 0L
    private var activeSince = 0L
    private var rumbleUntil = 0L
    private var lastInput: ByteArray? = null
    private var guideSent = false

    init {
        function.eventListener = { event ->
            when (event) {
                FfsEvent.ENABLE -> enqueue(Enable)
                FfsEvent.DISABLE, FfsEvent.UNBIND -> enqueue(Disable)
                FfsEvent.SUSPEND -> rumble.stop()
                else -> Unit
            }
        }
        if (function.isEnabled) enqueue(Enable)
        Thread(::writeLoop, "gip-in").start()
        Thread(::readLoop, "gip-out").start()
    }

    override fun setButton(bit: Int, pressed: Boolean) {
        synchronized(lock) { state.buttons = if (pressed) state.buttons or bit else state.buttons and bit.inv() }
        if (bit == GamepadReport.BUTTON_GUIDE) enqueue(Guide(pressed)) else enqueue(Input(payload()))
    }

    override fun setHat(hat: Int) {
        synchronized(lock) { state.hat = hat }
        enqueue(Input(payload()))
    }

    override fun setTrigger(left: Boolean, value: Int) {
        val crossed = synchronized(lock) {
            val previous = if (left) state.leftTrigger else state.rightTrigger
            if (left) state.leftTrigger = value else state.rightTrigger = value
            (previous == 0) != (value == 0)
        }
        if (crossed) enqueue(Input(payload())) else requestFlush()
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
        enqueue(Input(payload()))
        enqueue(Guide(false))
        rumble.stop()
    }

    override fun close() {
        closed = true
        queue.clear()
        queue.offerFirst(Close)
        function.eventListener = {}
        rumble.stop()
    }

    private fun payload(): ByteArray = synchronized(lock) { GipReport.inputPayload(state) }

    private fun requestFlush() {
        if (flushPending.compareAndSet(false, true)) enqueue(Flush)
    }

    private fun enqueue(item: Item) {
        while (!queue.offerLast(item)) {
            val victim = queue.firstOrNull { it is Input || it === Flush } ?: queue.peekFirst() ?: continue
            if (queue.remove(victim) && victim === Flush) flushPending.set(false)
        }
    }

    private fun writeLoop() {
        while (!closed) {
            val now = SystemClock.elapsedRealtime()
            val wait = (nextDeadline() - now).coerceIn(0, PadIo.IDLE_POLL_MS)
            val item = queue.pollFirst(wait, TimeUnit.MILLISECONDS)
            if (item === Close) break
            if (item != null) handle(item)
            runTimers(SystemClock.elapsedRealtime())
        }
    }

    private fun nextDeadline(): Long {
        var deadline = Long.MAX_VALUE
        if (phase == Phase.ARRIVAL) deadline = minOf(deadline, nextHelloAt)
        if (phase == Phase.ACTIVE) deadline = minOf(deadline, nextStatusAt)
        if (rumbleUntil != 0L) deadline = minOf(deadline, rumbleUntil)
        return if (deadline == Long.MAX_VALUE) SystemClock.elapsedRealtime() + PadIo.IDLE_POLL_MS else deadline
    }

    private fun runTimers(now: Long) {
        if (phase == Phase.ARRIVAL && now >= nextHelloAt) {
            send(GipReport.hello(nextSystem(), deviceId, vendorId, productId))
            nextHelloAt = now + HELLO_INTERVAL_MS
        }
        if (phase == Phase.ACTIVE && now >= nextStatusAt) {
            send(GipReport.status(nextSystem()))
            nextStatusAt = now + if (now - activeSince < STATUS_FAST_WINDOW_MS) STATUS_FAST_MS else STATUS_SLOW_MS
        }
        if (rumbleUntil != 0L && now >= rumbleUntil) {
            rumbleUntil = 0L
            rumble.stop()
        }
    }

    private fun handle(item: Item) {
        val now = SystemClock.elapsedRealtime()
        when (item) {
            Enable -> enterArrival(now)
            Disable -> {
                phase = Phase.IDLE
                stopRumble()
            }
            MetadataRequest -> {
                phase = Phase.METADATA
                metadataSequence = nextSystem()
                GipReport.metadataPackets(metadataSequence).forEach(::send)
            }
            is MetadataAck -> if (item.received >= GipReport.METADATA.size) {
                send(GipReport.metadataComplete(metadataSequence))
            }
            is SetState -> when (item.state) {
                GipReport.STATE_START -> {
                    phase = Phase.ACTIVE
                    activeSince = now
                    send(GipReport.status(nextSystem()))
                    nextStatusAt = now + STATUS_FAST_MS
                    lastInput = null
                    sendInput(payload())
                }
                GipReport.STATE_STOP -> {
                    phase = Phase.STOPPED
                    stopRumble()
                }
                GipReport.STATE_OFF -> {
                    send(GipReport.status(nextSystem(), poweringOff = true))
                    phase = Phase.IDLE
                    stopRumble()
                }
                GipReport.STATE_RESET -> {
                    send(GipReport.status(nextSystem(), poweringOff = true))
                    stopRumble()
                    enterArrival(now + HELLO_INTERVAL_MS)
                }
                else -> Unit
            }
            is Motor -> {
                val motor = item.motor
                rumble.set(motor.left, motor.right)
                rumbleUntil = if (motor.left == 0 && motor.right == 0) 0L else now + motor.durationMs
            }
            is Raw -> send(item.packet)
            is Input -> sendInput(item.payload)
            Flush -> {
                flushPending.set(false)
                sendInput(payload())
            }
            is Guide -> if (phase == Phase.ACTIVE && item.pressed != guideSent) {
                send(GipReport.guide(nextSystem(), item.pressed))
                guideSent = item.pressed
            }
            Close -> Unit
        }
    }

    private fun enterArrival(helloAt: Long) {
        phase = Phase.ARRIVAL
        nextHelloAt = helloAt
        guideSent = false
        lastInput = null
    }

    private fun stopRumble() {
        rumbleUntil = 0L
        rumble.stop()
    }

    private fun sendInput(payload: ByteArray) {
        if (phase != Phase.ACTIVE) return
        val last = lastInput
        if (last != null && last.contentEquals(payload)) return
        if (send(GipReport.input(nextInput(), payload))) lastInput = payload
    }

    private fun send(packet: ByteArray): Boolean {
        return when (val result = input.write(packet)) {
            is FfsEndpoint.IoResult.Done -> true
            is FfsEndpoint.IoResult.NotEnabled -> false
            is FfsEndpoint.IoResult.Failed -> {
                if (!closed) {
                    AppLog.w(TAG, "write failed", result.error)
                    Thread.sleep(PadIo.RETRY_DELAY_MS)
                }
                false
            }
        }
    }

    private fun readLoop() {
        val buffer = ByteArray(GipReport.PACKET_SIZE)
        while (!closed) {
            when (val result = output.read(buffer)) {
                is FfsEndpoint.IoResult.Done -> GipReport.decode(buffer, result.count).forEach(::dispatch)
                is FfsEndpoint.IoResult.NotEnabled -> Thread.sleep(PadIo.NOT_ENABLED_RETRY_MS)
                else -> Thread.sleep(PadIo.RETRY_DELAY_MS)
            }
        }
    }

    private fun dispatch(message: GipReport.Message) {
        if (closed) return
        AppLog.i(TAG) {
            "host %02x %02x seq %d: %s".format(message.type, message.flags, message.sequence,
                message.payload.joinToString(" ") { "%02x".format(it) })
        }
        if (message.wantsAck) enqueue(Raw(GipReport.ack(message)))
        when {
            message.type == GipReport.CMD_ACK && message.isSystem -> GipReport.ackedLength(message)?.let { (command, received) ->
                if (command == GipReport.CMD_METADATA) enqueue(MetadataAck(received))
            }
            message.type == GipReport.CMD_METADATA && message.isSystem && message.length == 0 -> enqueue(MetadataRequest)
            message.type == GipReport.CMD_SET_STATE && message.isSystem && message.length >= 1 ->
                enqueue(SetState(message.payload[0].toInt() and 0xFF))
            message.type == GipReport.CMD_MOTOR && !message.isSystem -> GipReport.motor(message)?.let { enqueue(Motor(it)) }
        }
    }

    private fun nextSystem(): Int {
        systemSequence = GipReport.nextSequence(systemSequence)
        return systemSequence
    }

    private fun nextInput(): Int {
        inputSequence = GipReport.nextSequence(inputSequence)
        return inputSequence
    }

    private companion object {
        const val TAG = "GipOutput"
        const val HELLO_INTERVAL_MS = 500L
        const val STATUS_FAST_MS = 1000L
        const val STATUS_SLOW_MS = 20_000L
        const val STATUS_FAST_WINDOW_MS = 10_000L
    }
}
