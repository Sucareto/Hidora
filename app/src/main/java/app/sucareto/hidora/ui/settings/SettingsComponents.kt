package app.sucareto.hidora.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import app.sucareto.hidora.R
import app.sucareto.hidora.ui.theme.Dimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScaffold(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = Dimens.ScreenPadding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Dimens.CardSpacing),
            content = content
        )
    }
}

@Composable
fun SettingsSection(title: String) {
    HorizontalDivider()
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

@Composable
fun SettingsNote(text: String, warning: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private const val DISABLED_ALPHA = 0.38f

@Composable
fun NavigationSetting(title: String, summary: String? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = Dimens.SectionSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (summary != null) SettingsNote(summary)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
    }
}

@Composable
fun SwitchSetting(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    summary: String? = null,
    enabled: Boolean = true
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) Color.Unspecified else MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_ALPHA)
            )
            if (summary != null) SettingsNote(summary)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> ChoiceSetting(title: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, summary: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.SectionSpacing)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (value, label) ->
                SegmentedButton(
                    selected = value == selected,
                    onClick = { onSelect(value) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size)
                ) { Text(label) }
            }
        }
        if (summary != null) SettingsNote(summary)
    }
}

@Composable
fun TextSetting(title: String, value: String, maxUtf8Bytes: Int, onCommit: (String) -> Unit) {
    var text by remember { mutableStateOf(value) }
    var committed by remember { mutableStateOf(value) }
    if (value != committed) {
        committed = value
        text = value
    }
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            text = input.takeUtf8Bytes(maxUtf8Bytes)
            if (text.isNotBlank()) {
                committed = text.trim()
                onCommit(committed)
            }
        },
        label = { Text(title) },
        singleLine = true,
        isError = text.isBlank(),
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth()
    )
}

private fun String.takeUtf8Bytes(limit: Int): String {
    var bytes = 0
    var end = 0
    while (end < length) {
        val codePoint = codePointAt(end)
        val size = when {
            codePoint < 0x80 -> 1
            codePoint < 0x800 -> 2
            codePoint < 0x10000 -> 3
            else -> 4
        }
        if (bytes + size > limit) break
        bytes += size
        end += Character.charCount(codePoint)
    }
    return substring(0, end)
}

@Composable
fun HexSetting(title: String, value: Int, onCommit: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf("%04X".format(value)) }
    val valid = text.length == 4 && (text.toIntOrNull(16) ?: 0) != 0
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            text = input.filter { it in '0'..'9' || it.uppercaseChar() in 'A'..'F' }.uppercase().take(4)
            if (text.length == 4) text.toIntOrNull(16)?.takeIf { it != 0 }?.let(onCommit)
        },
        label = { Text(title) },
        prefix = { Text("0x") },
        singleLine = true,
        isError = !valid,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, keyboardType = KeyboardType.Ascii),
        modifier = Modifier.fillMaxWidth()
    )
}
