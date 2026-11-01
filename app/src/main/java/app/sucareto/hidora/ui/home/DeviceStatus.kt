package app.sucareto.hidora.ui.home

import android.content.Context
import androidx.annotation.StringRes
import app.sucareto.hidora.R
import app.sucareto.hidora.core.gadget.GadgetReply

internal enum class GadgetAction(@StringRes val labelRes: Int) {
    START(R.string.action_enter),
    STOP(R.string.action_stop)
}

internal sealed interface DeviceStatus {
    data object OpenError : DeviceStatus
    data object DescriptorRejected : DeviceStatus
    data object PreviewMode : DeviceStatus
    data class UdcNotRestored(val action: GadgetAction) : DeviceStatus
    data class ActionResult(val action: GadgetAction, val success: Boolean, val errorCode: String?) : DeviceStatus
}

internal fun DeviceStatus.toMessage(context: Context): String = when (this) {
    DeviceStatus.OpenError -> context.getString(R.string.status_open_error)
    DeviceStatus.DescriptorRejected -> context.getString(R.string.status_descriptor_rejected)
    DeviceStatus.PreviewMode -> context.getString(R.string.status_preview_mode)
    is DeviceStatus.UdcNotRestored -> context.getString(R.string.result_udc_not_restored, context.getString(action.labelRes))
    is DeviceStatus.ActionResult -> {
        val label = context.getString(action.labelRes)
        if (success) context.getString(R.string.result_success, label)
        else context.getString(R.string.result_failure, label, friendlyErrorText(context, errorCode))
    }
}

private fun friendlyErrorText(context: Context, errorCode: String?): String = when (errorCode) {
    GadgetReply.ERROR_CODE_DEVICE_EXHAUSTED -> context.getString(R.string.error_device_exhausted)
    GadgetReply.ERROR_CODE_SLOT_UNAVAILABLE -> context.getString(R.string.error_slot_unavailable)
    null -> context.getString(R.string.error_unknown)
    else -> errorCode
}
