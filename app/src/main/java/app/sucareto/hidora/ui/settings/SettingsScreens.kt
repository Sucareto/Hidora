package app.sucareto.hidora.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.sucareto.hidora.R
import app.sucareto.hidora.core.profile.AppSettings
import app.sucareto.hidora.core.profile.GadgetProfile
import app.sucareto.hidora.core.profile.UsbIdentity
import app.sucareto.hidora.profile.ProfileRegistry
import app.sucareto.hidora.profile.usbIdentity

@Composable
fun AppSettingsScreen(onOpenAdvanced: () -> Unit, onOpenEnvironment: () -> Unit, onBack: () -> Unit) {
    val settings = AppSettings.get(LocalContext.current)
    SettingsScaffold(title = stringResource(R.string.settings_app_title), onBack = onBack) {
        SwitchSetting(
            title = stringResource(R.string.settings_stop_on_leave),
            summary = stringResource(R.string.settings_stop_on_leave_summary),
            checked = settings.stopOnLeave,
            onCheckedChange = { settings.stopOnLeave = it }
        )
        NavigationSetting(
            title = stringResource(R.string.settings_advanced),
            summary = stringResource(R.string.settings_advanced_summary),
            onClick = onOpenAdvanced
        )
        NavigationSetting(
            title = stringResource(R.string.settings_environment),
            summary = stringResource(R.string.settings_environment_summary),
            onClick = onOpenEnvironment
        )
    }
}

@Composable
fun AdvancedSettingsScreen(isDevicePresent: (GadgetProfile) -> Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val rows = ProfileRegistry.profiles.map { profile -> profile to profile.usbIdentities.map { profile.usbIdentity(context, it) } }
    val idCounts = rows.flatMap { it.second }.groupingBy { it.vendorId to it.productId }.eachCount()
    SettingsScaffold(title = stringResource(R.string.settings_advanced), onBack = onBack) {
        SettingsNote(stringResource(R.string.settings_vid_pid_warning), warning = rows.any { (_, entries) -> entries.any { it.isTestId } })
        for ((profile, entries) in rows) {
            if (entries.isEmpty()) continue
            SettingsSection(stringResource(profile.titleRes))
            if (isDevicePresent(profile)) SettingsNote(stringResource(R.string.settings_rebuild_hint), warning = true)
            val active = if (entries.size > 1) profile.activeIdentity(context) else null
            for (entry in entries) {
                entry.defaults.titleRes?.let { title ->
                    val label = stringResource(title)
                    Text(
                        if (entry.defaults === active) stringResource(R.string.settings_identity_active, label) else label,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                entry.defaults.noteRes?.let { SettingsNote(stringResource(it)) }
                TextSetting(stringResource(R.string.settings_device_name), entry.product, UsbIdentity.PRODUCT_MAX_BYTES) { entry.product = it }
                HexSetting(stringResource(R.string.settings_vendor_id), entry.vendorId, { entry.vendorId = it })
                HexSetting(stringResource(R.string.settings_product_id), entry.productId, { entry.productId = it })
                if ((idCounts[entry.vendorId to entry.productId] ?: 0) > 1) {
                    SettingsNote(stringResource(R.string.settings_vid_pid_duplicate), warning = true)
                }
                TextButton(onClick = entry::reset, enabled = entry.isCustom) { Text(stringResource(R.string.settings_reset_default)) }
            }
        }
    }
}

@Composable
fun ProfileSettingsScreen(profile: GadgetProfile, deviceExists: Boolean, onBack: () -> Unit) {
    SettingsScaffold(title = stringResource(R.string.settings_profile_title, stringResource(profile.titleRes)), onBack = onBack) {
        if (deviceExists) SettingsNote(stringResource(R.string.settings_rebuild_hint), warning = true)
        profile.Settings()
    }
}
