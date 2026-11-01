package app.sucareto.hidora.profile.xinput

import app.sucareto.hidora.core.ffs.FfsDescriptors
import app.sucareto.hidora.profile.gamepad.GamepadReport
import app.sucareto.hidora.profile.gamepad.GamepadState
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlin.math.roundToInt

object GipReport {
    const val PACKET_SIZE = 64
    const val DEVICE_ID_LENGTH = 6

    const val CMD_ACK = 0x01
    private const val CMD_HELLO = 0x02
    private const val CMD_STATUS = 0x03
    const val CMD_METADATA = 0x04
    const val CMD_SET_STATE = 0x05
    private const val CMD_SECURITY = 0x06
    private const val CMD_GUIDE = 0x07
    const val CMD_MOTOR = 0x09
    private const val CMD_LED = 0x0A
    private const val CMD_INPUT = 0x20

    private const val FLAG_FRAGMENT = 0x80
    private const val FLAG_INIT_FRAGMENT = 0x40
    private const val FLAG_SYSTEM = 0x20
    private const val FLAG_ACME = 0x10
    private const val FLAG_EXTENSION_MASK = 0x07

    const val STATE_START = 0x00
    const val STATE_STOP = 0x01
    const val STATE_OFF = 0x04
    const val STATE_RESET = 0x07

    const val DEVICE_CLASS = 0xFF
    const val DEVICE_SUBCLASS = 0x47
    const val DEVICE_PROTOCOL = 0xD0

    private const val EP_IN = 0x81
    private const val EP_OUT = 0x01
    private const val FS_INTERVAL = 4
    private const val HS_INTERVAL = 6
    private const val MAX_SEQUENCE = 255

    private const val FIRMWARE_MAJOR = 1
    private const val FIRMWARE_MINOR = 0
    private const val FIRMWARE_BUILD = 1
    private const val FIRMWARE_REVISION = 0
    private const val HARDWARE_MAJOR = 1
    private const val HARDWARE_MINOR = 0
    private val SUB_VERSION = byteArrayOf(1, 0)
    private const val FRAGMENT_PAYLOAD = 58
    private const val INPUT_LENGTH = 14
    private const val FUNCTION_MAP_LENGTH = 18
    private const val FUNCTION_SHARE = 0x01
    private const val MOTOR_LENGTH = 9
    private const val MOTOR_RIGHT = 0x01
    private const val MOTOR_LEFT = 0x02
    private const val MOTOR_RIGHT_TRIGGER = 0x04
    private const val MOTOR_LEFT_TRIGGER = 0x08
    private const val MOTOR_TIME_UNIT_MS = 10
    private const val VK_GUIDE = 0x5B
    private const val STATUS_FULL_POWER = 0x80
    private const val TRIGGER_MAX = 1023

    private const val MESSAGE_UPSTREAM = 0x10
    private const val MESSAGE_DOWNSTREAM = 0x08
    private const val METADATA_HEADER_LENGTH = 16
    private const val DEVICE_HEADER_LENGTH = 22
    private const val MESSAGE_ENTRY_LENGTH = 23

    private const val IFACE_CONTROLLER = "9776FF56-9BFD-4581-AD45-B645BBA526D6"
    private const val IFACE_GAMEPAD = "082E402C-07DF-45E1-A5AB-A3127AF197B5"
    private const val IFACE_NAVIGATION = "B8F31FE7-7386-40E9-A9F8-2F21263ACFB7"
    private const val IFACE_CONSOLE_FUNCTION_MAP = "ECDDD2FE-D387-4294-BD96-1A712E3DC77D"
    private const val IFACE_PC_SECURITY_OPT_OUT = "7A34CE77-7DE2-45C6-8CA4-0042C08BD94A"

    class Message(val type: Int, val flags: Int, val sequence: Int, val length: Int, val offset: Int, val payload: ByteArray) {
        val isSystem: Boolean get() = flags and FLAG_SYSTEM != 0
        val wantsAck: Boolean get() = flags and FLAG_ACME != 0
        val isFragment: Boolean get() = flags and FLAG_FRAGMENT != 0
    }

    private class MessageSpec(val type: Int, val length: Int, val upstream: Boolean)

    class Motor(val left: Int, val right: Int, val durationMs: Long)

    val DESCRIPTORS: ByteArray = run {
        val iface = FfsDescriptors.interfaceDescriptor(0, 2, DEVICE_CLASS, DEVICE_SUBCLASS, DEVICE_PROTOCOL)
        val fsIn = FfsDescriptors.interruptEndpoint(EP_IN, PACKET_SIZE, FS_INTERVAL)
        val fsOut = FfsDescriptors.interruptEndpoint(EP_OUT, PACKET_SIZE, FS_INTERVAL)
        val hsIn = FfsDescriptors.interruptEndpoint(EP_IN, PACKET_SIZE, HS_INTERVAL)
        val hsOut = FfsDescriptors.interruptEndpoint(EP_OUT, PACKET_SIZE, HS_INTERVAL)
        val companion = FfsDescriptors.superSpeedCompanion(PACKET_SIZE)
        FfsDescriptors.descriptors(
            fullSpeed = listOf(iface, fsIn, fsOut),
            highSpeed = listOf(iface, hsIn, hsOut),
            superSpeed = listOf(iface, hsIn, companion, hsOut, companion),
            extCompat = listOf(FfsDescriptors.ExtCompat(0, "XGIP10"))
        )
    }

    val METADATA: ByteArray = metadata(
        firmware = FIRMWARE_MAJOR to FIRMWARE_MINOR,
        inCommands = listOf(CMD_ACK, CMD_HELLO, CMD_STATUS, CMD_METADATA, CMD_SECURITY, CMD_GUIDE),
        outCommands = listOf(CMD_ACK, CMD_METADATA, CMD_SET_STATE, CMD_SECURITY, CMD_LED),
        preferredTypes = listOf("Windows.Xbox.Input.Gamepad"),
        interfaces = listOf(IFACE_CONTROLLER, IFACE_GAMEPAD, IFACE_NAVIGATION, IFACE_CONSOLE_FUNCTION_MAP, IFACE_PC_SECURITY_OPT_OUT),
        messages = listOf(
            MessageSpec(CMD_INPUT, INPUT_LENGTH + FUNCTION_MAP_LENGTH, upstream = true),
            MessageSpec(CMD_MOTOR, MOTOR_LENGTH, upstream = false)
        )
    )

    private val BUTTON_MAP = listOf(
        GamepadReport.BUTTON_MENU to 0x0004,
        GamepadReport.BUTTON_VIEW to 0x0008,
        GamepadReport.BUTTON_A to 0x0010,
        GamepadReport.BUTTON_B to 0x0020,
        GamepadReport.BUTTON_X to 0x0040,
        GamepadReport.BUTTON_Y to 0x0080,
        GamepadReport.BUTTON_LB to 0x1000,
        GamepadReport.BUTTON_RB to 0x2000,
        GamepadReport.BUTTON_LS to 0x4000,
        GamepadReport.BUTTON_RS to 0x8000
    )

    private const val DPAD_UP = 0x0100
    private const val DPAD_DOWN = 0x0200
    private const val DPAD_LEFT = 0x0400
    private const val DPAD_RIGHT = 0x0800

    private val HAT_MAP = mapOf(
        GamepadReport.HAT_UP to DPAD_UP,
        GamepadReport.HAT_UP_RIGHT to (DPAD_UP or DPAD_RIGHT),
        GamepadReport.HAT_RIGHT to DPAD_RIGHT,
        GamepadReport.HAT_DOWN_RIGHT to (DPAD_DOWN or DPAD_RIGHT),
        GamepadReport.HAT_DOWN to DPAD_DOWN,
        GamepadReport.HAT_DOWN_LEFT to (DPAD_DOWN or DPAD_LEFT),
        GamepadReport.HAT_LEFT to DPAD_LEFT,
        GamepadReport.HAT_UP_LEFT to (DPAD_UP or DPAD_LEFT)
    )

    fun serialNumber(deviceId: ByteArray): String =
        deviceId.reversed().joinToString("") { "%02X".format(it) }.padStart(32, '0')

    fun nextSequence(current: Int): Int = current % MAX_SEQUENCE + 1

    private fun packet(type: Int, flags: Int, sequence: Int, payload: ByteArray, offset: Int? = null): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(type)
        out.write(flags)
        out.write(sequence)
        val length = varint(payload.size)
        val tail = offset?.let { varint(it) } ?: ByteArray(0)
        if ((3 + length.size + tail.size) % 2 != 0) {
            length[length.size - 1] = (length[length.size - 1].toInt() or 0x80).toByte()
            out.write(length)
            out.write(0)
        } else {
            out.write(length)
        }
        out.write(tail)
        out.write(payload)
        return out.toByteArray()
    }

    fun decode(data: ByteArray, length: Int): List<Message> {
        val messages = mutableListOf<Message>()
        var pos = 0
        while (pos + 4 <= length) {
            val type = data[pos].toInt() and 0xFF
            val flags = data[pos + 1].toInt() and 0xFF
            val sequence = data[pos + 2].toInt() and 0xFF
            pos += 3
            val (payloadLength, lengthBytes) = readVarint(data, pos, length)
            pos += lengthBytes
            var offset = 0
            if (flags and FLAG_FRAGMENT != 0) {
                val (value, offsetBytes) = readVarint(data, pos, length)
                offset = value
                pos += offsetBytes
            }
            if (pos + payloadLength > length) break
            messages += Message(type, flags, sequence, payloadLength, offset, data.copyOfRange(pos, pos + payloadLength))
            pos += payloadLength
        }
        return messages
    }

    fun hello(sequence: Int, deviceId: ByteArray, vendorId: Int, productId: Int): ByteArray {
        val payload = ByteArrayOutputStream().apply {
            write(deviceId.copyOf(DEVICE_ID_LENGTH))
            write(ByteArray(2))
            u16(vendorId)
            u16(productId)
            u16(FIRMWARE_MAJOR)
            u16(FIRMWARE_MINOR)
            u16(FIRMWARE_BUILD)
            u16(FIRMWARE_REVISION)
            write(HARDWARE_MAJOR)
            write(HARDWARE_MINOR)
            repeat(3) { write(SUB_VERSION) }
        }.toByteArray()
        return packet(CMD_HELLO, FLAG_SYSTEM, sequence, payload)
    }

    fun status(sequence: Int, poweringOff: Boolean = false): ByteArray =
        packet(CMD_STATUS, FLAG_SYSTEM, sequence, byteArrayOf(if (poweringOff) 0 else STATUS_FULL_POWER.toByte(), 0, 0, 0))

    fun guide(sequence: Int, pressed: Boolean): ByteArray =
        packet(CMD_GUIDE, FLAG_SYSTEM, sequence, byteArrayOf(if (pressed) 1 else 0, VK_GUIDE.toByte()))

    fun input(sequence: Int, payload: ByteArray): ByteArray = packet(CMD_INPUT, 0, sequence, payload)

    fun inputPayload(state: GamepadState): ByteArray {
        var buttons = HAT_MAP[state.hat] ?: 0
        for ((bit, gip) in BUTTON_MAP) if (state.buttons and bit != 0) buttons = buttons or gip
        val payload = ByteArray(INPUT_LENGTH + FUNCTION_MAP_LENGTH)
        payload[0] = buttons.toByte()
        payload[1] = (buttons shr 8).toByte()
        putU16(payload, 2, trigger(state.leftTrigger))
        putU16(payload, 4, trigger(state.rightTrigger))
        putU16(payload, 6, axis(state.leftX))
        putU16(payload, 8, axis(-state.leftY))
        putU16(payload, 10, axis(state.rightX))
        putU16(payload, 12, axis(-state.rightY))
        if (state.buttons and GamepadReport.BUTTON_SHARE != 0) payload[INPUT_LENGTH] = FUNCTION_SHARE.toByte()
        return payload
    }

    fun ack(message: Message): ByteArray {
        val received = if (message.isFragment && message.flags and FLAG_INIT_FRAGMENT == 0) message.offset + message.length else message.length
        val payload = byteArrayOf(
            0, message.type.toByte(), (FLAG_SYSTEM or (message.flags and FLAG_EXTENSION_MASK)).toByte(),
            received.toByte(), (received shr 8).toByte(), 0, 0, 0, 0
        )
        return packet(CMD_ACK, FLAG_SYSTEM, message.sequence, payload)
    }

    fun ackedLength(message: Message): Pair<Int, Int>? {
        if (message.type != CMD_ACK || message.payload.size < 5) return null
        val p = message.payload
        return (p[1].toInt() and 0xFF) to ((p[3].toInt() and 0xFF) or ((p[4].toInt() and 0xFF) shl 8))
    }

    fun metadataPackets(sequence: Int): List<ByteArray> {
        val blob = METADATA
        if (blob.size <= FRAGMENT_PAYLOAD) {
            return listOf(packet(CMD_METADATA, FLAG_SYSTEM or FLAG_ACME, sequence, blob))
        }
        val packets = mutableListOf<ByteArray>()
        var offset = 0
        while (offset < blob.size) {
            val end = minOf(offset + FRAGMENT_PAYLOAD, blob.size)
            val first = offset == 0
            val last = end == blob.size
            var flags = FLAG_FRAGMENT or FLAG_SYSTEM
            if (first) flags = flags or FLAG_INIT_FRAGMENT
            if (first || last) flags = flags or FLAG_ACME
            packets += packet(CMD_METADATA, flags, sequence, blob.copyOfRange(offset, end), if (first) blob.size else offset)
            offset = end
        }
        return packets
    }

    fun metadataComplete(sequence: Int): ByteArray =
        packet(CMD_METADATA, FLAG_FRAGMENT or FLAG_SYSTEM, sequence, ByteArray(0), METADATA.size)

    fun motor(message: Message): Motor? {
        val p = message.payload
        if (message.type != CMD_MOTOR || p.size < MOTOR_LENGTH || p[0].toInt() != 0) return null
        val bitmap = p[1].toInt() and 0xFF
        val leftImpulse = if (bitmap and MOTOR_LEFT_TRIGGER != 0) p[2].toInt() and 0xFF else 0
        val rightImpulse = if (bitmap and MOTOR_RIGHT_TRIGGER != 0) p[3].toInt() and 0xFF else 0
        val left = maxOf(if (bitmap and MOTOR_LEFT != 0) p[4].toInt() and 0xFF else 0, leftImpulse)
        val right = maxOf(if (bitmap and MOTOR_RIGHT != 0) p[5].toInt() and 0xFF else 0, rightImpulse)
        val duration = p[6].toInt() and 0xFF
        val delay = p[7].toInt() and 0xFF
        val repeat = p[8].toInt() and 0xFF
        if (duration == 0) return Motor(0, 0, 0)
        return Motor(level(left), level(right), (delay + duration).toLong() * (repeat + 1) * MOTOR_TIME_UNIT_MS)
    }

    private fun metadata(
        firmware: Pair<Int, Int>,
        inCommands: List<Int>,
        outCommands: List<Int>,
        preferredTypes: List<String>,
        interfaces: List<String>,
        messages: List<MessageSpec>
    ): ByteArray {
        val sections = listOf(
            ByteArrayOutputStream().apply { write(1); u16(firmware.first); u16(firmware.second) }.toByteArray(),
            byteArrayOf(0),
            byteArrayOf(inCommands.size.toByte()) + ByteArray(inCommands.size) { inCommands[it].toByte() },
            byteArrayOf(outCommands.size.toByte()) + ByteArray(outCommands.size) { outCommands[it].toByte() },
            ByteArrayOutputStream().apply {
                write(preferredTypes.size)
                for (type in preferredTypes) {
                    val ascii = type.toByteArray(Charsets.US_ASCII)
                    u16(ascii.size)
                    write(ascii)
                }
            }.toByteArray(),
            ByteArrayOutputStream().apply {
                write(interfaces.size)
                interfaces.forEach { write(guid(it)) }
            }.toByteArray()
        )
        val device = ByteArrayOutputStream()
        device.u16(DEVICE_HEADER_LENGTH + sections.sumOf { it.size })
        var offset = DEVICE_HEADER_LENGTH
        for (section in sections) {
            device.u16(offset)
            offset += section.size
        }
        device.write(ByteArray(8))
        sections.forEach { device.write(it) }
        val messageBlock = ByteArrayOutputStream().apply {
            write(messages.size)
            for (message in messages) {
                u16(MESSAGE_ENTRY_LENGTH)
                write(message.type)
                u16(message.length)
                u16(1)
                u16(if (message.upstream) MESSAGE_UPSTREAM else MESSAGE_DOWNSTREAM)
                u16(0)
                write(ByteArray(12))
            }
        }
        val body = device.toByteArray() + messageBlock.toByteArray()
        return ByteArrayOutputStream().apply {
            u16(METADATA_HEADER_LENGTH)
            u16(1)
            u16(0)
            write(ByteArray(8))
            u16(METADATA_HEADER_LENGTH + body.size)
            write(body)
        }.toByteArray()
    }

    private fun guid(value: String): ByteArray {
        val uuid = UUID.fromString(value)
        val msb = uuid.mostSignificantBits
        val lsb = uuid.leastSignificantBits
        val out = ByteArray(16)
        val data1 = (msb ushr 32).toInt()
        for (i in 0 until 4) out[i] = (data1 shr (8 * i)).toByte()
        val data2 = (msb ushr 16).toInt() and 0xFFFF
        out[4] = data2.toByte()
        out[5] = (data2 shr 8).toByte()
        val data3 = msb.toInt() and 0xFFFF
        out[6] = data3.toByte()
        out[7] = (data3 shr 8).toByte()
        for (i in 0 until 8) out[8 + i] = (lsb ushr (8 * (7 - i))).toByte()
        return out
    }

    private fun varint(value: Int): ByteArray {
        val out = ByteArrayOutputStream()
        var v = value
        do {
            var b = v and 0x7F
            v = v ushr 7
            if (v != 0) b = b or 0x80
            out.write(b)
        } while (v != 0)
        return out.toByteArray()
    }

    private fun readVarint(data: ByteArray, start: Int, limit: Int): Pair<Int, Int> {
        var value = 0
        var i = 0
        while (i < 4 && start + i < limit) {
            val b = data[start + i].toInt() and 0xFF
            value = value or ((b and 0x7F) shl (7 * i))
            i++
            if (b and 0x80 == 0) break
        }
        return value to i
    }

    private fun trigger(value: Int): Int = (value.coerceIn(0, GamepadReport.TRIGGER_MAX) * TRIGGER_MAX + GamepadReport.TRIGGER_MAX / 2) / GamepadReport.TRIGGER_MAX

    private fun axis(value: Float): Int = (value.coerceIn(-1f, 1f) * GamepadReport.AXIS_MAX).roundToInt()

    private fun level(percent: Int): Int = (percent.coerceIn(0, 100) * 255 + 50) / 100

    private fun putU16(target: ByteArray, offset: Int, value: Int) {
        target[offset] = value.toByte()
        target[offset + 1] = (value shr 8).toByte()
    }

    private fun ByteArrayOutputStream.u16(value: Int) {
        write(value and 0xFF)
        write(value shr 8 and 0xFF)
    }
}
