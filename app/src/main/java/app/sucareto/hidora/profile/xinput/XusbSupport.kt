package app.sucareto.hidora.profile.xinput

import android.content.Context
import android.system.Os
import androidx.core.content.edit

object XusbSupport {
    private const val KEY_REJECTED_KERNEL = "xusb_rejected_kernel"
    private const val FIRST_RESTRICTED_MAJOR = 5

    fun isSupported(context: Context): Boolean =
        kernelAllows(Os.uname().release) && prefs(context).getString(KEY_REJECTED_KERNEL, null) != kernelBuild()

    fun markRejected(context: Context) = prefs(context).edit { putString(KEY_REJECTED_KERNEL, kernelBuild()) }

    private fun kernelAllows(release: String): Boolean {
        val major = release.substringBefore('.').toIntOrNull() ?: return true
        return major < FIRST_RESTRICTED_MAJOR
    }

    private fun kernelBuild(): String = Os.uname().let { "${it.release} ${it.version}" }

    private fun prefs(context: Context) = context.getSharedPreferences(XInputIdentity.PREFS, Context.MODE_PRIVATE)
}
