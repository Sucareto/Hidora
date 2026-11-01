package app.sucareto.hidora.profile.keyboardmouse

object MouseReporter {
    const val BUTTON_LEFT: Int = 0x01
    const val BUTTON_RIGHT: Int = 0x02
    const val BUTTON_MIDDLE: Int = 0x04
    const val MAX_DELTA = 127

    fun buildReport(buttons: Int, dx: Int, dy: Int, wheel: Int): ByteArray {
        return byteArrayOf(
            buttons.toByte(),
            dx.coerceIn(-MAX_DELTA, MAX_DELTA).toByte(),
            dy.coerceIn(-MAX_DELTA, MAX_DELTA).toByte(),
            wheel.coerceIn(-MAX_DELTA, MAX_DELTA).toByte()
        )
    }
}
