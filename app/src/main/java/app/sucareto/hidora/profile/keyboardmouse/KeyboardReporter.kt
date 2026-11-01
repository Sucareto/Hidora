package app.sucareto.hidora.profile.keyboardmouse

object KeyboardReporter {
    const val MODIFIER_LEFT_CTRL: Int = 0x01
    const val MODIFIER_LEFT_SHIFT: Int = 0x02
    const val MODIFIER_LEFT_ALT: Int = 0x04
    const val MODIFIER_LEFT_GUI: Int = 0x08
    const val MODIFIER_RIGHT_SHIFT: Int = 0x20

    const val LED_CAPS_LOCK: Int = 0x02

    private const val BOOT_KEYS_OFFSET = 2
    private const val BOOT_MAX_KEYS = 6
    private const val NKRO_USAGE_MIN = 0x04
    private const val NKRO_USAGE_MAX = 0xDD

    fun buildReport(modifier: Int, keyCodes: List<Int>): ByteArray {
        val report = ByteArray(KeyboardMouseDescriptors.BOOT_REPORT_LENGTH)
        report[0] = modifier.toByte()
        for (i in 0 until minOf(BOOT_MAX_KEYS, keyCodes.size)) {
            report[BOOT_KEYS_OFFSET + i] = keyCodes[i].toByte()
        }
        return report
    }

    fun buildRelease(): ByteArray = ByteArray(KeyboardMouseDescriptors.BOOT_REPORT_LENGTH)

    fun buildNkroReport(modifier: Int, pressedKeyCodes: Set<Int>): ByteArray {
        val report = ByteArray(KeyboardMouseDescriptors.NKRO_REPORT_LENGTH)
        report[0] = modifier.toByte()
        for (keyCode in pressedKeyCodes) {
            if (keyCode < NKRO_USAGE_MIN || keyCode > NKRO_USAGE_MAX) continue
            val bitIndex = keyCode - NKRO_USAGE_MIN
            val byteIndex = 1 + bitIndex / 8
            report[byteIndex] = (report[byteIndex].toInt() or (1 shl (bitIndex % 8))).toByte()
        }
        return report
    }
}
