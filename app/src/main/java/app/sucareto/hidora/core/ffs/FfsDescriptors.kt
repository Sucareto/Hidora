package app.sucareto.hidora.core.ffs

import java.io.ByteArrayOutputStream

object FfsDescriptors {
    private const val DESCRIPTORS_MAGIC_V2 = 3
    private const val STRINGS_MAGIC = 2

    private const val HAS_FS_DESC = 1
    private const val HAS_HS_DESC = 2
    private const val HAS_SS_DESC = 4
    private const val HAS_MS_OS_DESC = 8
    const val ALL_CTRL_RECIP = 64

    private const val INTERFACE_LENGTH = 9
    private const val ENDPOINT_LENGTH = 7
    private const val SS_COMPANION_LENGTH = 6
    private const val DT_INTERFACE = 0x04
    private const val DT_ENDPOINT = 0x05
    private const val DT_SS_ENDPOINT_COMPANION = 0x30
    private const val XFER_INTERRUPT = 0x03

    private const val OS_DESC_HEADER_LENGTH = 11
    private const val EXT_COMPAT_LENGTH = 24
    private const val OS_DESC_BCD_VERSION = 1
    private const val OS_DESC_EXT_COMPAT_INDEX = 4
    private const val COMPATIBLE_ID_LENGTH = 8
    private const val EXT_COMPAT_RESERVED_LENGTH = 6

    class ExtCompat(val firstInterface: Int, val compatibleId: String)

    fun interfaceDescriptor(number: Int, endpoints: Int, cls: Int, subclass: Int, protocol: Int): ByteArray =
        bytes(INTERFACE_LENGTH, DT_INTERFACE, number, 0, endpoints, cls, subclass, protocol, 0)

    fun interruptEndpoint(address: Int, maxPacket: Int, interval: Int): ByteArray =
        bytes(ENDPOINT_LENGTH, DT_ENDPOINT, address, XFER_INTERRUPT, maxPacket and 0xFF, maxPacket shr 8, interval)

    fun superSpeedCompanion(bytesPerInterval: Int): ByteArray =
        bytes(SS_COMPANION_LENGTH, DT_SS_ENDPOINT_COMPANION, 0, 0, bytesPerInterval and 0xFF, bytesPerInterval shr 8)

    fun descriptors(
        fullSpeed: List<ByteArray>,
        highSpeed: List<ByteArray>,
        superSpeed: List<ByteArray>,
        extCompat: List<ExtCompat>,
        extraFlags: Int = 0
    ): ByteArray {
        var flags = HAS_FS_DESC or HAS_HS_DESC or extraFlags
        if (superSpeed.isNotEmpty()) flags = flags or HAS_SS_DESC
        if (extCompat.isNotEmpty()) flags = flags or HAS_MS_OS_DESC
        val body = ByteArrayOutputStream()
        body.u32(flags)
        body.u32(fullSpeed.size)
        body.u32(highSpeed.size)
        if (superSpeed.isNotEmpty()) body.u32(superSpeed.size)
        if (extCompat.isNotEmpty()) body.u32(1)
        (fullSpeed + highSpeed + superSpeed).forEach { body.write(it) }
        if (extCompat.isNotEmpty()) {
            body.write(0)
            body.u32(OS_DESC_HEADER_LENGTH + EXT_COMPAT_LENGTH * extCompat.size)
            body.u16(OS_DESC_BCD_VERSION)
            body.u16(OS_DESC_EXT_COMPAT_INDEX)
            body.write(extCompat.size)
            body.write(0)
            for (entry in extCompat) {
                body.write(entry.firstInterface)
                body.write(0)
                body.write(fixedAscii(entry.compatibleId, COMPATIBLE_ID_LENGTH))
                body.write(ByteArray(COMPATIBLE_ID_LENGTH))
                body.write(ByteArray(EXT_COMPAT_RESERVED_LENGTH))
            }
        }
        return withHeader(DESCRIPTORS_MAGIC_V2, body.toByteArray())
    }

    fun emptyStrings(): ByteArray = withHeader(STRINGS_MAGIC, ByteArrayOutputStream().apply { u32(0); u32(0) }.toByteArray())

    private fun withHeader(magic: Int, body: ByteArray): ByteArray = ByteArrayOutputStream().apply {
        u32(magic)
        u32(body.size + 8)
        write(body)
    }.toByteArray()

    private fun fixedAscii(value: String, length: Int): ByteArray {
        val raw = value.toByteArray(Charsets.US_ASCII)
        require(raw.size <= length) { "id too long: $value" }
        return raw.copyOf(length)
    }

    fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    private fun ByteArrayOutputStream.u16(value: Int) {
        write(value and 0xFF)
        write(value shr 8 and 0xFF)
    }

    private fun ByteArrayOutputStream.u32(value: Int) {
        u16(value and 0xFFFF)
        u16(value ushr 16)
    }
}
