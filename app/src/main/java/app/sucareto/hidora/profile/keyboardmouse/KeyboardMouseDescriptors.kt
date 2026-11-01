package app.sucareto.hidora.profile.keyboardmouse

import app.sucareto.hidora.core.gadget.hidDescriptor

object KeyboardMouseDescriptors {
    val KEYBOARD_BOOT: ByteArray = hidDescriptor(
        "05 01 09 06 a1 01 05 07 19 e0 29 e7 15 00 25 01" +
            "75 01 95 08 81 02 95 01 75 08 81 03 95 05 75 01" +
            "05 08 19 01 29 05 91 02 95 01 75 03 91 03 95 06" +
            "75 08 15 00 25 65 05 07 19 00 29 65 81 01 c0"
    )

    const val BOOT_REPORT_LENGTH = 8

    val KEYBOARD_NKRO: ByteArray = hidDescriptor(
        "05 01 09 06 a1 01" +
            "05 07 19 e0 29 e7 15 00 25 01 75 01 95 08 81 02" +
            "19 04 29 dd 15 00 25 01 75 01 95 da 81 02" +
            "95 06 75 01 81 03" +
            "c0"
    )

    const val NKRO_REPORT_LENGTH = 29

    val MOUSE: ByteArray = hidDescriptor(
        "05 01 09 02 a1 01 09 01 a1 00 05 09 19 01 29 05" +
            "15 00 25 01 95 05 75 01 81 02 95 01 75 03 81 01" +
            "05 01 09 30 09 31 09 38 15 81 25 7f 75 08 95 03" +
            "81 06 c0 c0"
    )

    const val MOUSE_REPORT_LENGTH = 4
}
