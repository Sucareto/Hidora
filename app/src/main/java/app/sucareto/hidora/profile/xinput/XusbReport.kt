package app.sucareto.hidora.profile.xinput

import app.sucareto.hidora.core.ffs.FfsDescriptors
import app.sucareto.hidora.profile.gamepad.GamepadReport
import app.sucareto.hidora.profile.gamepad.GamepadState
import kotlin.math.roundToInt

object XusbReport {
    private const val LENGTH = 20
    const val DEVICE_CLASS = 0xFF
    const val DEVICE_SUBCLASS = 0xFF
    const val DEVICE_PROTOCOL = 0xFF
    const val BCD_DEVICE = 0x0114

    const val REQ_SET_CONTROL = 0x00
    const val REQ_GET_REPORT = 0x01
    const val REQ_GET_DEVICE_ID = 0x01

    private const val INTERFACE_CLASS = 0xFF
    private const val INTERFACE_SUBCLASS = 0x5D
    private const val INTERFACE_PROTOCOL = 0x01
    private const val EP_IN = 0x81
    private const val EP_OUT = 0x01
    private const val PACKET_SIZE = 32
    private const val IN_INTERVAL = 4
    private const val OUT_INTERVAL = 8
    private const val XUSB_DESCRIPTOR_LENGTH = 9
    private const val XUSB_DESCRIPTOR_TYPE = 0x21
    private const val XUSB_VERSION = 0x0100
    private const val XUSB_SUBTYPE = 0x01
    private const val ENTRY_IN = 0x20
    private const val ENTRY_OUT = 0x10

    private const val OUT_RUMBLE = 0x00
    private const val OUT_RUMBLE_LENGTH = 0x08
    private const val RUMBLE_MIN_LENGTH = 5

    private const val DPAD_UP = 0x0001
    private const val DPAD_DOWN = 0x0002
    private const val DPAD_LEFT = 0x0004
    private const val DPAD_RIGHT = 0x0008

    private val BUTTON_MAP = listOf(
        GamepadReport.BUTTON_MENU to 0x0010,
        GamepadReport.BUTTON_VIEW to 0x0020,
        GamepadReport.BUTTON_LS to 0x0040,
        GamepadReport.BUTTON_RS to 0x0080,
        GamepadReport.BUTTON_LB to 0x0100,
        GamepadReport.BUTTON_RB to 0x0200,
        GamepadReport.BUTTON_GUIDE to 0x0400,
        GamepadReport.BUTTON_A to 0x1000,
        GamepadReport.BUTTON_B to 0x2000,
        GamepadReport.BUTTON_X to 0x4000,
        GamepadReport.BUTTON_Y to 0x8000
    )

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

    val DESCRIPTORS: ByteArray = run {
        val iface = FfsDescriptors.interfaceDescriptor(0, 2, INTERFACE_CLASS, INTERFACE_SUBCLASS, INTERFACE_PROTOCOL)
        val xusb = FfsDescriptors.bytes(
            XUSB_DESCRIPTOR_LENGTH, XUSB_DESCRIPTOR_TYPE, XUSB_VERSION and 0xFF, XUSB_VERSION shr 8, XUSB_SUBTYPE,
            ENTRY_IN, EP_IN, ENTRY_OUT, EP_OUT
        )
        val epIn = FfsDescriptors.interruptEndpoint(EP_IN, PACKET_SIZE, IN_INTERVAL)
        val epOut = FfsDescriptors.interruptEndpoint(EP_OUT, PACKET_SIZE, OUT_INTERVAL)
        val companion = FfsDescriptors.superSpeedCompanion(PACKET_SIZE)
        FfsDescriptors.descriptors(
            fullSpeed = listOf(iface, xusb, epIn, epOut),
            highSpeed = listOf(iface, xusb, epIn, epOut),
            superSpeed = listOf(iface, xusb, epIn, companion, epOut, companion),
            extCompat = listOf(FfsDescriptors.ExtCompat(0, "XUSB10")),
            extraFlags = FfsDescriptors.ALL_CTRL_RECIP
        )
    }

    fun build(state: GamepadState): ByteArray {
        var buttons = HAT_MAP[state.hat] ?: 0
        for ((bit, xusb) in BUTTON_MAP) if (state.buttons and bit != 0) buttons = buttons or xusb
        val report = ByteArray(LENGTH)
        report[1] = LENGTH.toByte()
        report[2] = buttons.toByte()
        report[3] = (buttons shr 8).toByte()
        report[4] = state.leftTrigger.coerceIn(0, GamepadReport.TRIGGER_MAX).toByte()
        report[5] = state.rightTrigger.coerceIn(0, GamepadReport.TRIGGER_MAX).toByte()
        putAxis(report, 6, state.leftX)
        putAxis(report, 8, -state.leftY)
        putAxis(report, 10, state.rightX)
        putAxis(report, 12, -state.rightY)
        return report
    }

    fun rumble(data: ByteArray, length: Int): Pair<Int, Int>? {
        if (length < RUMBLE_MIN_LENGTH || data[0].toInt() != OUT_RUMBLE || data[1].toInt() != OUT_RUMBLE_LENGTH) return null
        return (data[3].toInt() and 0xFF) to (data[4].toInt() and 0xFF)
    }

    fun serialNumber(deviceId: ByteArray): String = deviceId.joinToString("") { "%02X".format(it) }

    private fun putAxis(report: ByteArray, offset: Int, value: Float) {
        val raw = (value.coerceIn(-1f, 1f) * GamepadReport.AXIS_MAX).roundToInt()
        report[offset] = raw.toByte()
        report[offset + 1] = (raw shr 8).toByte()
    }
}
