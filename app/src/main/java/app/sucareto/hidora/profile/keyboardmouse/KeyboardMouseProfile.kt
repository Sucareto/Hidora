package app.sucareto.hidora.profile.keyboardmouse

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import app.sucareto.hidora.R
import app.sucareto.hidora.core.gadget.GadgetSpec
import app.sucareto.hidora.core.gadget.HidFunctionSpec
import app.sucareto.hidora.core.profile.DeviceIo
import app.sucareto.hidora.core.profile.GadgetProfile
import app.sucareto.hidora.core.profile.ProfileSession
import app.sucareto.hidora.core.profile.UsbIdentity
import app.sucareto.hidora.profile.usbIdentity

object KeyboardMouseProfile : GadgetProfile {
    const val FUNCTION_KEYBOARD_BOOT = "keyboard_boot"
    const val FUNCTION_KEYBOARD_NKRO = "keyboard_nkro"
    const val FUNCTION_MOUSE = "mouse"
    private val IDENTITY = UsbIdentity("default", product = "Keyboard & Mouse")

    override val id = "keyboard_mouse"
    override val titleRes = R.string.device_row_keyboard_mouse
    override val gadgetName = "STDKB"
    override val immersiveLandscape = true
    override val hasSettings = true
    override val usbIdentities = listOf(IDENTITY)

    override fun spec(context: Context): GadgetSpec {
        val boot = HidFunctionSpec(FUNCTION_KEYBOARD_BOOT, protocol = 1, subclass = 1,
            reportLength = KeyboardMouseDescriptors.BOOT_REPORT_LENGTH,
            reportDescriptor = KeyboardMouseDescriptors.KEYBOARD_BOOT)
        val nkro = HidFunctionSpec(FUNCTION_KEYBOARD_NKRO, protocol = 0, subclass = 0,
            reportLength = KeyboardMouseDescriptors.NKRO_REPORT_LENGTH, reportDescriptor = KeyboardMouseDescriptors.KEYBOARD_NKRO)
        val mouse = HidFunctionSpec(FUNCTION_MOUSE, protocol = 2, subclass = 1,
            reportLength = KeyboardMouseDescriptors.MOUSE_REPORT_LENGTH, reportDescriptor = KeyboardMouseDescriptors.MOUSE)
        return GadgetSpec(
            name = gadgetName,
            usb = usbIdentity(context, IDENTITY).deviceInfo(),
            functions = listOf(boot, nkro, mouse)
        )
    }

    override fun createSession(context: Context, spec: GadgetSpec, io: DeviceIo): ProfileSession =
        KeyboardMouseSession(KeyboardMouseOutput(io.hid), KeyboardMouseSettings.get(context))

    @Composable
    override fun Settings() = KeyboardMouseSettingsContent(KeyboardMouseSettings.get(LocalContext.current))
}
