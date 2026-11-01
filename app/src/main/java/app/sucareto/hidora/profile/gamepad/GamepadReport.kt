package app.sucareto.hidora.profile.gamepad

import app.sucareto.hidora.core.gadget.hidDescriptor
import kotlin.math.roundToInt

class GamepadState {
    var buttons = 0
    var hat = GamepadReport.HAT_NONE
    var leftX = 0f
    var leftY = 0f
    var rightX = 0f
    var rightY = 0f
    var leftTrigger = 0
    var rightTrigger = 0

    val hasHeldInputs: Boolean
        get() = buttons != 0 || hat != GamepadReport.HAT_NONE || leftTrigger != 0 || rightTrigger != 0

    fun reset() {
        buttons = 0
        hat = GamepadReport.HAT_NONE
        leftX = 0f
        leftY = 0f
        rightX = 0f
        rightY = 0f
        leftTrigger = 0
        rightTrigger = 0
    }
}

object GamepadReport {
    const val BUTTON_A = 1 shl 0
    const val BUTTON_B = 1 shl 1
    const val BUTTON_X = 1 shl 2
    const val BUTTON_Y = 1 shl 3
    const val BUTTON_LB = 1 shl 4
    const val BUTTON_RB = 1 shl 5
    const val BUTTON_VIEW = 1 shl 6
    const val BUTTON_MENU = 1 shl 7
    const val BUTTON_GUIDE = 1 shl 8
    const val BUTTON_LS = 1 shl 9
    const val BUTTON_RS = 1 shl 10
    const val BUTTON_SHARE = 1 shl 11

    const val HAT_UP = 0
    const val HAT_UP_RIGHT = 1
    const val HAT_RIGHT = 2
    const val HAT_DOWN_RIGHT = 3
    const val HAT_DOWN = 4
    const val HAT_DOWN_LEFT = 5
    const val HAT_LEFT = 6
    const val HAT_UP_LEFT = 7
    const val HAT_NONE = 8

    const val AXIS_MAX = 32767
    const val TRIGGER_MAX = 255

    const val REPORT_LENGTH = 13

    val DESCRIPTOR: ByteArray = hidDescriptor(
        "05 01 09 05 a1 01" +
            "05 09 09 01 09 02 09 04 09 05 09 07 09 08 09 0b 09 0c 09 0d 09 0e 09 0f 09 10" +
            "15 00 25 01 75 01 95 0c 81 02" +
            "75 04 95 01 81 03" +
            "05 01 09 30 09 31 09 33 09 34 16 01 80 26 ff 7f 75 10 95 04 81 02" +
            "09 32 09 35 15 00 26 ff 00 75 08 95 02 81 02" +
            "09 39 15 00 25 07 35 00 46 3b 01 65 14 75 04 95 01 81 42" +
            "75 04 95 01 81 03" +
            "c0"
    )

    fun build(state: GamepadState): ByteArray {
        val report = ByteArray(REPORT_LENGTH)
        report[0] = state.buttons.toByte()
        report[1] = (state.buttons shr 8).toByte()
        putAxis(report, 2, state.leftX)
        putAxis(report, 4, state.leftY)
        putAxis(report, 6, state.rightX)
        putAxis(report, 8, state.rightY)
        report[10] = state.leftTrigger.coerceIn(0, TRIGGER_MAX).toByte()
        report[11] = state.rightTrigger.coerceIn(0, TRIGGER_MAX).toByte()
        report[12] = state.hat.coerceIn(0, HAT_NONE).toByte()
        return report
    }

    fun buildNeutral(): ByteArray = build(GamepadState())

    private fun putAxis(report: ByteArray, offset: Int, value: Float) {
        val raw = (value.coerceIn(-1f, 1f) * AXIS_MAX).roundToInt()
        report[offset] = raw.toByte()
        report[offset + 1] = (raw shr 8).toByte()
    }
}
