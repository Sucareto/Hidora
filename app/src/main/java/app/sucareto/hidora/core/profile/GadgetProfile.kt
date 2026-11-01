package app.sucareto.hidora.core.profile

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import app.sucareto.hidora.core.ffs.FfsFunctions
import app.sucareto.hidora.core.gadget.GadgetSpec
import app.sucareto.hidora.core.hid.HidEndpoints

interface GadgetProfile {
    val id: String

    @get:StringRes
    val titleRes: Int

    val gadgetName: String

    val immersiveLandscape: Boolean get() = false

    val hasSettings: Boolean get() = false

    val usbIdentities: List<UsbIdentity> get() = emptyList()

    fun activeIdentity(context: Context): UsbIdentity? = usbIdentities.singleOrNull()

    fun spec(context: Context): GadgetSpec

    fun createSession(context: Context, spec: GadgetSpec, io: DeviceIo): ProfileSession

    fun createPreviewSession(context: Context): ProfileSession {
        val spec = spec(context)
        return createSession(context, spec, DeviceIo(HidEndpoints.preview(spec.functions.map { it.name }), FfsFunctions.EMPTY))
    }

    fun onDescriptorsRejected(context: Context, spec: GadgetSpec) {}

    @Composable
    fun Settings() {}
}

interface ProfileSession {
    @Composable
    fun Content(onLeave: () -> Unit)

    fun releaseInputs() {}

    fun close() {}
}

class DeviceIo(val hid: HidEndpoints, val ffs: FfsFunctions) {
    fun closeAll() {
        hid.closeAll()
        ffs.closeAll()
    }
}
