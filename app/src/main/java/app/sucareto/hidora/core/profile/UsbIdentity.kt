package app.sucareto.hidora.core.profile

import android.content.Context
import androidx.annotation.StringRes
import app.sucareto.hidora.core.gadget.UsbDeviceInfo
import java.util.concurrent.ConcurrentHashMap

data class UsbId(val vendorId: Int, val productId: Int)

class UsbIdentity(
    val key: String,
    val product: String,
    @StringRes val titleRes: Int? = null,
    @StringRes val noteRes: Int? = null,
    val fixedId: UsbId? = null
) {
    companion object {
        const val PRODUCT_MAX_BYTES = 126
    }
}

object UsbIdAllocator {
    fun allocate(identities: List<Pair<String, UsbIdentity>>): Map<String, UsbId> {
        val keys = identities.map { (owner, identity) -> "$owner.${identity.key}" }
        require(keys.toSet().size == keys.size) { "duplicate usb identity key" }
        val fixed = identities.mapNotNull { it.second.fixedId }
        require(fixed.toSet().size == fixed.size) { "duplicate fixed usb id" }
        val free = UsbDeviceInfo.PID_CODES_TEST_PRODUCT_IDS
            .map { UsbId(UsbDeviceInfo.PID_CODES_VENDOR_ID, it) }
            .filter { it !in fixed }
            .iterator()
        return keys.zip(identities).associate { (key, pair) ->
            val identity = pair.second
            key to (identity.fixedId ?: run {
                require(free.hasNext()) { "out of pid.codes test product ids at $key" }
                free.next()
            })
        }
    }
}

class UsbIdentitySettings private constructor(context: Context) : PrefsStore(context, "usb_identity") {
    inner class Entry(private val prefix: String, val defaults: UsbIdentity, val defaultId: UsbId) {
        var product by string("$prefix.product", defaults.product)
        var vendorId by int("$prefix.vendor_id", defaultId.vendorId)
        var productId by int("$prefix.product_id", defaultId.productId)

        val isTestId: Boolean
            get() = vendorId == UsbDeviceInfo.PID_CODES_VENDOR_ID && productId in UsbDeviceInfo.PID_CODES_TEST_PRODUCT_IDS

        val isCustom: Boolean
            get() = product != defaults.product || UsbId(vendorId, productId) != defaultId

        fun reset() {
            product = defaults.product
            vendorId = defaultId.vendorId
            productId = defaultId.productId
            remove("$prefix.product", "$prefix.vendor_id", "$prefix.product_id")
        }

        fun deviceInfo(
            serialNumber: String = "",
            bcdDevice: Int = UsbDeviceInfo.DEFAULT_BCD_DEVICE,
            deviceClass: Int = 0,
            deviceSubClass: Int = 0,
            deviceProtocol: Int = 0
        ) = UsbDeviceInfo(
            idVendor = vendorId,
            idProduct = productId,
            product = product,
            serialNumber = serialNumber,
            bcdDevice = bcdDevice,
            deviceClass = deviceClass,
            deviceSubClass = deviceSubClass,
            deviceProtocol = deviceProtocol
        )
    }

    private val entries = ConcurrentHashMap<String, Entry>()

    fun entry(profile: GadgetProfile, identity: UsbIdentity, defaultId: UsbId): Entry =
        entries.computeIfAbsent("${profile.id}.${identity.key}") { Entry(it, identity, defaultId) }

    companion object {
        @Volatile private var instance: UsbIdentitySettings? = null

        fun get(context: Context): UsbIdentitySettings =
            instance ?: synchronized(this) { instance ?: UsbIdentitySettings(context).also { instance = it } }
    }
}
