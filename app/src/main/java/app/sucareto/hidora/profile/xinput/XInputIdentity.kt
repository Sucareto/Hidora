package app.sucareto.hidora.profile.xinput

import android.content.Context
import androidx.core.content.edit
import kotlin.random.Random

object XInputIdentity {
    const val PREFS = "xinput"
    private const val KEY_XUSB_ID = "device_id"
    private const val KEY_GIP_ID = "gip_device_id"
    private const val XUSB_ID_LAST_BYTE_MASK = 0x0F

    fun xusbDeviceId(context: Context): ByteArray {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val id = if (prefs.contains(KEY_XUSB_ID)) {
            prefs.getInt(KEY_XUSB_ID, 0)
        } else {
            Random.nextInt().also { prefs.edit { putInt(KEY_XUSB_ID, it) } }
        }
        return byteArrayOf((id shr 24).toByte(), (id shr 16).toByte(), (id shr 8).toByte(), (id and XUSB_ID_LAST_BYTE_MASK).toByte())
    }

    fun gipDeviceId(context: Context): ByteArray {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val id = if (prefs.contains(KEY_GIP_ID)) {
            prefs.getLong(KEY_GIP_ID, 0)
        } else {
            (Random.nextLong() and 0xFFFF_FFFF_FFFFL or 1L).also { prefs.edit { putLong(KEY_GIP_ID, it) } }
        }
        return ByteArray(GipReport.DEVICE_ID_LENGTH) { (id shr (8 * it)).toByte() }
    }
}
