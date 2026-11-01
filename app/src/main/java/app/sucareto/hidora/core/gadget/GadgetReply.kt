package app.sucareto.hidora.core.gadget

object GadgetReply {
    const val KEY_SUCCESS = "success"
    const val KEY_ERROR = "error"
    const val KEY_ACTIVE_GADGET = "active_gadget"
    const val KEY_IS_BOUND = "is_bound"
    const val KEY_SYSTEM_UDC_RESTORED = "system_udc_restored"
    const val KEY_HID_HANDLES = "hid_handles"
    const val KEY_FFS_HANDLES = "ffs_handles"

    const val ERROR_CODE_DEVICE_EXHAUSTED = "DEVICE_EXHAUSTED"
    const val ERROR_CODE_SLOT_UNAVAILABLE = "SLOT_UNAVAILABLE"
    const val ERROR_CODE_DESCRIPTORS_REJECTED = "DESCRIPTORS_REJECTED"
}
