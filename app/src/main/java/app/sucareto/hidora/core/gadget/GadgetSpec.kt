package app.sucareto.hidora.core.gadget

import android.os.Bundle
import androidx.core.os.BundleCompat

data class UsbDeviceInfo(
    val idVendor: Int,
    val idProduct: Int,
    val product: String,
    val manufacturer: String = MANUFACTURER,
    val serialNumber: String = "",
    val bcdDevice: Int = DEFAULT_BCD_DEVICE,
    val bcdUsb: Int = 0x0200,
    val deviceClass: Int = 0,
    val deviceSubClass: Int = 0,
    val deviceProtocol: Int = 0
) {
    companion object {
        const val PID_CODES_VENDOR_ID = 0x1209
        const val MANUFACTURER = "Sucareto"
        val PID_CODES_TEST_PRODUCT_IDS = 0x0001..0x0010
        const val DEFAULT_BCD_DEVICE = 0x0100
    }
}

class HidFunctionSpec(
    val name: String,
    val protocol: Int,
    val subclass: Int,
    val reportLength: Int,
    val reportDescriptor: ByteArray
)

class FfsFunctionSpec(
    val name: String,
    val endpointCount: Int,
    val descriptors: ByteArray,
    val strings: ByteArray
)

class GadgetSpec(
    val name: String,
    val usb: UsbDeviceInfo,
    val functions: List<HidFunctionSpec>,
    val ffsFunctions: List<FfsFunctionSpec> = emptyList(),
    val osDescVendorCode: Int? = null,
    val maxPowerMa: Int = 250
) {
    fun instance(function: String) = "$name$INSTANCE_SEPARATOR$function"

    fun validate() {
        require(isValidName(name)) { "invalid gadget name: $name" }
        val names = functions.map { it.name } + ffsFunctions.map { it.name }
        names.forEach { require(FUNCTION_NAME_PATTERN.matches(it)) { "invalid function name: $it" } }
        require(names.toSet().size == names.size) { "duplicate function name in $name" }
    }

    fun toBundle(): Bundle = Bundle().apply {
        putString(KEY_NAME, name)
        putInt(KEY_ID_VENDOR, usb.idVendor)
        putInt(KEY_ID_PRODUCT, usb.idProduct)
        putInt(KEY_BCD_DEVICE, usb.bcdDevice)
        putInt(KEY_BCD_USB, usb.bcdUsb)
        putInt(KEY_DEVICE_CLASS, usb.deviceClass)
        putInt(KEY_DEVICE_SUBCLASS, usb.deviceSubClass)
        putInt(KEY_DEVICE_PROTOCOL, usb.deviceProtocol)
        putString(KEY_MANUFACTURER, usb.manufacturer)
        putString(KEY_PRODUCT, usb.product)
        putString(KEY_SERIAL, usb.serialNumber)
        putInt(KEY_MAX_POWER, maxPowerMa)
        putParcelableArrayList(KEY_FUNCTIONS, ArrayList(functions.map { it.toBundle() }))
        putParcelableArrayList(KEY_FFS_FUNCTIONS, ArrayList(ffsFunctions.map { it.toBundle() }))
        putInt(KEY_OS_DESC_VENDOR_CODE, osDescVendorCode ?: -1)
    }

    companion object {
        const val INSTANCE_SEPARATOR = '_'
        private val NAME_PATTERN = Regex("[A-Za-z0-9]+")
        private val FUNCTION_NAME_PATTERN = Regex("[A-Za-z0-9_]+")

        fun isValidName(name: String): Boolean = NAME_PATTERN.matches(name)

        private const val KEY_NAME = "name"
        private const val KEY_ID_VENDOR = "id_vendor"
        private const val KEY_ID_PRODUCT = "id_product"
        private const val KEY_BCD_DEVICE = "bcd_device"
        private const val KEY_BCD_USB = "bcd_usb"
        private const val KEY_DEVICE_CLASS = "device_class"
        private const val KEY_DEVICE_SUBCLASS = "device_subclass"
        private const val KEY_DEVICE_PROTOCOL = "device_protocol"
        private const val KEY_FFS_FUNCTIONS = "ffs_functions"
        private const val KEY_OS_DESC_VENDOR_CODE = "os_desc_vendor_code"
        private const val KEY_FFS_ENDPOINTS = "endpoints"
        private const val KEY_FFS_DESCRIPTORS = "descriptors"
        private const val KEY_FFS_STRINGS = "strings"
        private const val KEY_MANUFACTURER = "manufacturer"
        private const val KEY_PRODUCT = "product"
        private const val KEY_SERIAL = "serial"
        private const val KEY_MAX_POWER = "max_power"
        private const val KEY_FUNCTIONS = "functions"
        private const val KEY_FN_NAME = "name"
        private const val KEY_FN_PROTOCOL = "protocol"
        private const val KEY_FN_SUBCLASS = "subclass"
        private const val KEY_FN_REPORT_LENGTH = "report_length"
        private const val KEY_FN_REPORT_DESC = "report_desc"

        fun fromBundle(b: Bundle): GadgetSpec = GadgetSpec(
            name = b.getString(KEY_NAME).orEmpty(),
            usb = UsbDeviceInfo(
                idVendor = b.getInt(KEY_ID_VENDOR),
                idProduct = b.getInt(KEY_ID_PRODUCT),
                manufacturer = b.getString(KEY_MANUFACTURER).orEmpty(),
                product = b.getString(KEY_PRODUCT).orEmpty(),
                serialNumber = b.getString(KEY_SERIAL).orEmpty(),
                bcdDevice = b.getInt(KEY_BCD_DEVICE),
                bcdUsb = b.getInt(KEY_BCD_USB),
                deviceClass = b.getInt(KEY_DEVICE_CLASS),
                deviceSubClass = b.getInt(KEY_DEVICE_SUBCLASS),
                deviceProtocol = b.getInt(KEY_DEVICE_PROTOCOL)
            ),
            functions = BundleCompat.getParcelableArrayList(b, KEY_FUNCTIONS, Bundle::class.java)
                .orEmpty().map { functionFromBundle(it) },
            ffsFunctions = BundleCompat.getParcelableArrayList(b, KEY_FFS_FUNCTIONS, Bundle::class.java)
                .orEmpty().map { ffsFunctionFromBundle(it) },
            osDescVendorCode = b.getInt(KEY_OS_DESC_VENDOR_CODE, -1).takeIf { it >= 0 },
            maxPowerMa = b.getInt(KEY_MAX_POWER)
        )

        private fun FfsFunctionSpec.toBundle(): Bundle = Bundle().apply {
            putString(KEY_FN_NAME, name)
            putInt(KEY_FFS_ENDPOINTS, endpointCount)
            putByteArray(KEY_FFS_DESCRIPTORS, descriptors)
            putByteArray(KEY_FFS_STRINGS, strings)
        }

        private fun ffsFunctionFromBundle(b: Bundle) = FfsFunctionSpec(
            name = b.getString(KEY_FN_NAME).orEmpty(),
            endpointCount = b.getInt(KEY_FFS_ENDPOINTS),
            descriptors = b.getByteArray(KEY_FFS_DESCRIPTORS) ?: ByteArray(0),
            strings = b.getByteArray(KEY_FFS_STRINGS) ?: ByteArray(0)
        )

        private fun HidFunctionSpec.toBundle(): Bundle = Bundle().apply {
            putString(KEY_FN_NAME, name)
            putInt(KEY_FN_PROTOCOL, protocol)
            putInt(KEY_FN_SUBCLASS, subclass)
            putInt(KEY_FN_REPORT_LENGTH, reportLength)
            putByteArray(KEY_FN_REPORT_DESC, reportDescriptor)
        }

        private fun functionFromBundle(b: Bundle) = HidFunctionSpec(
            name = b.getString(KEY_FN_NAME).orEmpty(),
            protocol = b.getInt(KEY_FN_PROTOCOL),
            subclass = b.getInt(KEY_FN_SUBCLASS),
            reportLength = b.getInt(KEY_FN_REPORT_LENGTH),
            reportDescriptor = b.getByteArray(KEY_FN_REPORT_DESC) ?: ByteArray(0)
        )
    }
}

fun hidDescriptor(hex: String): ByteArray {
    val clean = hex.filterNot { it.isWhitespace() }
    require(clean.length % 2 == 0) { "invalid hex length: ${clean.length}" }
    return ByteArray(clean.length / 2) { i -> clean.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
}
