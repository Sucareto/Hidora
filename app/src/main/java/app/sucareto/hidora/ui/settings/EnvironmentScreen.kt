package app.sucareto.hidora.ui.settings

import android.content.ClipData
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import app.sucareto.hidora.BuildConfig
import app.sucareto.hidora.R
import app.sucareto.hidora.core.gadget.CheckStatus
import app.sucareto.hidora.core.gadget.EnvCheck
import app.sucareto.hidora.core.gadget.EnvChecks
import app.sucareto.hidora.ui.theme.Dimens
import kotlinx.coroutines.launch

@Composable
fun EnvironmentScreen(runChecks: suspend () -> List<EnvCheck>, onBack: () -> Unit) {
    var checks by remember { mutableStateOf<List<EnvCheck>?>(null) }
    var round by remember { mutableIntStateOf(0) }
    val resources = LocalResources.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(round) {
        checks = null
        checks = runChecks()
    }
    SettingsScaffold(title = stringResource(R.string.settings_environment), onBack = onBack) {
        val results = checks
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SectionSpacing)) {
            Button(onClick = { round++ }, enabled = results != null) { Text(stringResource(R.string.env_recheck)) }
            OutlinedButton(
                onClick = {
                    val text = results.orEmpty().joinToString("\n", prefix = "Hidora ${BuildConfig.VERSION_NAME}\n") { check ->
                        "${resources.getString(titleRes(check.id))}: ${check.status} ${check.detail}".trimEnd()
                    }
                    scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("environment", text))) }
                },
                enabled = results != null
            ) { Text(stringResource(R.string.env_copy)) }
        }
        if (results == null) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        } else {
            results.forEach { EnvCheckRow(it) }
        }
    }
}

@Composable
private fun EnvCheckRow(check: EnvCheck) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(titleRes(check.id)), style = MaterialTheme.typography.bodyLarge)
            if (check.detail.isNotEmpty()) {
                Text(check.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            hintRes(check)?.let { SettingsNote(stringResource(it), warning = check.status == CheckStatus.FAIL) }
        }
        Text(stringResource(statusRes(check.status)), style = MaterialTheme.typography.labelLarge, color = statusColor(check.status))
    }
}

@Composable
private fun statusColor(status: CheckStatus): Color = when (status) {
    CheckStatus.OK -> MaterialTheme.colorScheme.primary
    CheckStatus.WARN -> MaterialTheme.colorScheme.tertiary
    CheckStatus.FAIL -> MaterialTheme.colorScheme.error
    CheckStatus.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
}

@StringRes
private fun statusRes(status: CheckStatus): Int = when (status) {
    CheckStatus.OK -> R.string.env_status_ok
    CheckStatus.WARN -> R.string.env_status_warn
    CheckStatus.FAIL -> R.string.env_status_fail
    CheckStatus.INFO -> R.string.env_status_info
}

@StringRes
private fun titleRes(id: String): Int = when (id) {
    EnvChecks.DEVICE -> R.string.env_device
    EnvChecks.ROOT_ACCESS -> R.string.env_root_access
    EnvChecks.ROOT_SOLUTION -> R.string.env_root_solution
    EnvChecks.CONFIGFS -> R.string.env_configfs
    EnvChecks.UDC -> R.string.env_udc
    EnvChecks.HID_FUNCTION -> R.string.env_hid_function
    EnvChecks.FUNCTIONFS -> R.string.env_functionfs
    EnvChecks.SLOT -> R.string.env_slot
    EnvChecks.SYSTEM_GADGETS -> R.string.env_system_gadgets
    EnvChecks.KERNEL -> R.string.env_kernel
    else -> R.string.env_unknown
}

@StringRes
private fun hintRes(check: EnvCheck): Int? = when (check.id to check.status) {
    EnvChecks.ROOT_ACCESS to CheckStatus.FAIL -> R.string.env_root_access_fail
    EnvChecks.ROOT_ACCESS to CheckStatus.WARN -> R.string.env_root_access_warn
    EnvChecks.ROOT_SOLUTION to CheckStatus.WARN -> R.string.env_root_solution_warn
    EnvChecks.CONFIGFS to CheckStatus.FAIL -> R.string.env_configfs_fail
    EnvChecks.UDC to CheckStatus.FAIL -> R.string.env_udc_fail
    EnvChecks.HID_FUNCTION to CheckStatus.FAIL -> R.string.env_hid_function_fail
    EnvChecks.HID_FUNCTION to CheckStatus.INFO -> R.string.env_hid_function_info
    EnvChecks.FUNCTIONFS to CheckStatus.WARN -> R.string.env_functionfs_warn
    EnvChecks.SLOT to CheckStatus.WARN -> R.string.env_slot_warn
    EnvChecks.KERNEL to CheckStatus.INFO -> R.string.env_kernel_info
    else -> null
}
