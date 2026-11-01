package app.sucareto.hidora.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import app.sucareto.hidora.R
import app.sucareto.hidora.ui.theme.Dimens

data class DeviceRowUiState(
    val profileId: String,
    val title: String,
    val stateLabel: String,
    val isActive: Boolean,
    val hasSettings: Boolean
)

data class DeviceActionsUiState(
    val stopEnabled: Boolean,
    val isBusy: Boolean,
    val onEnter: () -> Unit,
    val onStop: () -> Unit
)

data class DeviceSelectUiState(
    val previewMode: Boolean,
    val rows: List<DeviceRowUiState>,
    val selectedProfileId: String,
    val actions: DeviceActionsUiState
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceSelectScreen(
    state: DeviceSelectUiState,
    onSelect: (String) -> Unit,
    onOpenSettings: (String) -> Unit,
    onOpenAppSettings: () -> Unit
) {
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Dimens.SectionSpacing)) {
                            Text(stringResource(R.string.app_name))
                            if (state.previewMode) {
                                Text(
                                    text = stringResource(R.string.status_preview_label),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(bottom = Dimens.RootLabelBaselineOffset)
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = onOpenAppSettings) {
                            Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_app_title))
                        }
                    }
                )
                Box(modifier = Modifier.fillMaxWidth().height(Dimens.BusyIndicatorHeight)) {
                    if (state.actions.isBusy) LinearProgressIndicator(modifier = Modifier.fillMaxSize())
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = Dimens.ScreenPadding).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Dimens.CardSpacing)
        ) {
            state.rows.forEach { row -> DeviceCard(row, state.selectedProfileId == row.profileId, onSelect, onOpenSettings) }
            HorizontalDivider()
            val enabled = !state.actions.isBusy
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.ButtonRowSpacing)) {
                FilledTonalButton(onClick = state.actions.onStop, enabled = enabled && state.actions.stopEnabled, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_stop)) }
                Button(onClick = state.actions.onEnter, enabled = enabled, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_enter)) }
            }
        }
    }
}

@Composable
private fun DeviceCard(row: DeviceRowUiState, selected: Boolean, onSelect: (String) -> Unit, onOpenSettings: (String) -> Unit) {
    Card(
        onClick = { onSelect(row.profileId) },
        modifier = Modifier.fillMaxWidth().semantics {
            role = Role.RadioButton
            this.selected = selected
        },
        colors = if (selected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Dimens.CardPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = row.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            AssistChip(
                onClick = {},
                enabled = false,
                label = { Text(row.stateLabel) },
                colors = if (row.isActive) {
                    AssistChipDefaults.assistChipColors(
                        disabledContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        disabledLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                } else {
                    AssistChipDefaults.assistChipColors()
                }
            )
            if (row.hasSettings) {
                IconButton(onClick = { onOpenSettings(row.profileId) }) {
                    Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_profile_title, row.title))
                }
            } else {
                Spacer(Modifier.width(Dimens.SettingsIconSlot))
            }
        }
    }
}
