package app.sucareto.hidora.profile.gamepad

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import app.sucareto.hidora.R
import app.sucareto.hidora.core.ffs.FfsDescriptors
import app.sucareto.hidora.core.gadget.FfsFunctionSpec
import app.sucareto.hidora.core.gadget.GadgetSpec
import app.sucareto.hidora.core.gadget.HidFunctionSpec
import app.sucareto.hidora.core.hid.HidEndpoint
import app.sucareto.hidora.core.profile.DeviceIo
import app.sucareto.hidora.core.profile.GadgetProfile
import app.sucareto.hidora.core.profile.ProfileSession
import app.sucareto.hidora.core.profile.UsbIdentity
import app.sucareto.hidora.profile.usbIdentity
import app.sucareto.hidora.profile.xinput.GipOutput
import app.sucareto.hidora.profile.xinput.GipReport
import app.sucareto.hidora.profile.xinput.Rumble
import app.sucareto.hidora.profile.xinput.XInputIdentity
import app.sucareto.hidora.profile.xinput.XInputOutput
import app.sucareto.hidora.profile.xinput.XInputSession
import app.sucareto.hidora.profile.xinput.XusbReport
import app.sucareto.hidora.profile.xinput.XusbSupport

object GamepadProfile : GadgetProfile {
    const val FUNCTION_GAMEPAD = "gamepad"
    const val FUNCTION_XUSB = "xusb"
    const val FUNCTION_GIP = "gip"
    private const val MS_OS_VENDOR_CODE = 0x90
    private val IDENTITY_DINPUT = UsbIdentity("dinput", product = "Gamepad", titleRes = R.string.usb_identity_dinput)
    private val IDENTITY_GIP = UsbIdentity("gip", product = "XInput Gamepad", titleRes = R.string.usb_identity_gip,
        noteRes = R.string.usb_identity_ffs_note)
    private val IDENTITY_XUSB = UsbIdentity("xusb", product = "XUSB Gamepad", titleRes = R.string.usb_identity_xusb,
        noteRes = R.string.usb_identity_ffs_note)

    override val id = "gamepad"
    override val titleRes = R.string.device_row_gamepad
    override val gadgetName = "STDGP"
    override val immersiveLandscape = true
    override val hasSettings = true
    override val usbIdentities = listOf(IDENTITY_GIP, IDENTITY_DINPUT, IDENTITY_XUSB)

    override fun activeIdentity(context: Context): UsbIdentity {
        val settings = GamepadSettings.get(context)
        return when {
            settings.mode == GamepadMode.DINPUT -> IDENTITY_DINPUT
            settings.xbox360Compat && XusbSupport.isSupported(context) -> IDENTITY_XUSB
            else -> IDENTITY_GIP
        }
    }

    override fun spec(context: Context): GadgetSpec = when (activeIdentity(context)) {
        IDENTITY_DINPUT -> GadgetSpec(
            name = gadgetName,
            usb = usbIdentity(context, IDENTITY_DINPUT).deviceInfo(),
            functions = listOf(
                HidFunctionSpec(FUNCTION_GAMEPAD, protocol = 0, subclass = 0,
                    reportLength = GamepadReport.REPORT_LENGTH, reportDescriptor = GamepadReport.DESCRIPTOR)
            )
        )
        IDENTITY_XUSB -> xusbSpec(context)
        else -> gipSpec(context)
    }

    override fun onDescriptorsRejected(context: Context, spec: GadgetSpec) {
        if (spec.ffsFunctions.none { it.name == FUNCTION_XUSB }) return
        XusbSupport.markRejected(context)
        GamepadSettings.get(context).xbox360Compat = false
    }

    private fun xusbSpec(context: Context) = GadgetSpec(
        name = gadgetName,
        usb = usbIdentity(context, IDENTITY_XUSB).deviceInfo(
            serialNumber = XusbReport.serialNumber(XInputIdentity.xusbDeviceId(context)),
            bcdDevice = XusbReport.BCD_DEVICE,
            deviceClass = XusbReport.DEVICE_CLASS,
            deviceSubClass = XusbReport.DEVICE_SUBCLASS,
            deviceProtocol = XusbReport.DEVICE_PROTOCOL
        ),
        functions = emptyList(),
        ffsFunctions = listOf(
            FfsFunctionSpec(FUNCTION_XUSB, endpointCount = 2, descriptors = XusbReport.DESCRIPTORS, strings = FfsDescriptors.emptyStrings())
        ),
        osDescVendorCode = MS_OS_VENDOR_CODE
    )

    private fun gipSpec(context: Context) = GadgetSpec(
        name = gadgetName,
        usb = usbIdentity(context, IDENTITY_GIP).deviceInfo(
            serialNumber = GipReport.serialNumber(XInputIdentity.gipDeviceId(context)),
            deviceClass = GipReport.DEVICE_CLASS,
            deviceSubClass = GipReport.DEVICE_SUBCLASS,
            deviceProtocol = GipReport.DEVICE_PROTOCOL
        ),
        functions = emptyList(),
        ffsFunctions = listOf(
            FfsFunctionSpec(FUNCTION_GIP, endpointCount = 2, descriptors = GipReport.DESCRIPTORS, strings = FfsDescriptors.emptyStrings())
        ),
        osDescVendorCode = MS_OS_VENDOR_CODE
    )

    override fun createSession(context: Context, spec: GadgetSpec, io: DeviceIo): ProfileSession {
        val settings = GamepadSettings.get(context)
        val ffs = spec.ffsFunctions.map { it.name }
        val rumble = { Rumble(context) { settings.rumble } }
        return when {
            FUNCTION_GIP in ffs -> XInputSession(
                GipOutput(io.ffs[FUNCTION_GIP], XInputIdentity.gipDeviceId(context), spec.usb.idVendor, spec.usb.idProduct, rumble()),
                settings,
                share = true
            )
            FUNCTION_XUSB in ffs -> XInputSession(XInputOutput(io.ffs[FUNCTION_XUSB], XInputIdentity.xusbDeviceId(context), rumble()), settings)
            else -> GamepadSession(GamepadOutput(io.hid[FUNCTION_GAMEPAD]), settings)
        }
    }

    override fun createPreviewSession(context: Context): ProfileSession =
        GamepadSession(GamepadOutput(HidEndpoint.preview(FUNCTION_GAMEPAD)), GamepadSettings.get(context))

    @Composable
    override fun Settings() = GamepadSettingsContent(GamepadSettings.get(LocalContext.current))
}
