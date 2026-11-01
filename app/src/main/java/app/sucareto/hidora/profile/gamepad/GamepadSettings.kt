package app.sucareto.hidora.profile.gamepad

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.sucareto.hidora.R
import app.sucareto.hidora.core.profile.PrefsStore
import app.sucareto.hidora.profile.gamepad.ui.GamepadUiStyle
import app.sucareto.hidora.profile.xinput.XusbSupport
import app.sucareto.hidora.ui.settings.ChoiceSetting
import app.sucareto.hidora.ui.settings.SettingsSection
import app.sucareto.hidora.ui.settings.SwitchSetting

enum class GamepadMode { DINPUT, XINPUT }

enum class LabelStyle { XBOX, PS, NS }

class GamepadSettings private constructor(context: Context) : PrefsStore(context, "gamepad_settings") {
    var keyHaptics by boolean("key_haptics", false)
    var mode by enum("mode", GamepadMode.XINPUT)
    var xbox360Compat by boolean("xbox360_compat", false)
    var symmetricLayout by boolean("symmetric_layout", false)
    var labelStyle by enum("label_style", LabelStyle.XBOX)
    var swapShoulders by boolean("swap_shoulders", false)
    var hairTriggers by boolean("hair_triggers", false)
    var rumble by boolean("rumble", true)

    fun uiStyle(share: Boolean = false) = GamepadUiStyle(labelStyle, symmetricLayout, keyHaptics, swapShoulders, hairTriggers, share)

    companion object {
        @Volatile private var instance: GamepadSettings? = null

        fun get(context: Context): GamepadSettings =
            instance ?: synchronized(this) { instance ?: GamepadSettings(context).also { instance = it } }
    }
}

@Composable
fun GamepadSettingsContent(settings: GamepadSettings) {
    SwitchSetting(
        title = stringResource(R.string.settings_key_haptics),
        checked = settings.keyHaptics,
        onCheckedChange = { settings.keyHaptics = it }
    )
    SwitchSetting(
        title = stringResource(R.string.settings_gamepad_rumble),
        summary = stringResource(R.string.settings_gamepad_rumble_summary),
        checked = settings.rumble,
        onCheckedChange = { settings.rumble = it }
    )
    ChoiceSetting(
        title = stringResource(R.string.settings_gamepad_layout),
        options = listOf(false to stringResource(R.string.settings_gamepad_layout_offset), true to stringResource(R.string.settings_gamepad_layout_symmetric)),
        selected = settings.symmetricLayout,
        onSelect = { settings.symmetricLayout = it }
    )
    SwitchSetting(
        title = stringResource(R.string.settings_gamepad_swap_shoulders),
        summary = stringResource(R.string.settings_gamepad_swap_shoulders_summary),
        checked = settings.swapShoulders,
        onCheckedChange = { settings.swapShoulders = it }
    )
    ChoiceSetting(
        title = stringResource(R.string.settings_gamepad_triggers),
        options = listOf(false to stringResource(R.string.settings_gamepad_triggers_linear), true to stringResource(R.string.settings_gamepad_triggers_hair)),
        selected = settings.hairTriggers,
        onSelect = { settings.hairTriggers = it },
        summary = stringResource(R.string.settings_gamepad_triggers_summary)
    )
    ChoiceSetting(
        title = stringResource(R.string.settings_gamepad_labels),
        options = listOf(LabelStyle.XBOX to "Xbox", LabelStyle.PS to "PlayStation", LabelStyle.NS to "Nintendo"),
        selected = settings.labelStyle,
        onSelect = {
            settings.labelStyle = it
            settings.symmetricLayout = it == LabelStyle.PS
        },
        summary = stringResource(R.string.settings_gamepad_labels_summary)
    )
    SettingsSection(stringResource(R.string.settings_section_usb))
    ChoiceSetting(
        title = stringResource(R.string.settings_gamepad_mode),
        options = listOf(GamepadMode.XINPUT to "XInput", GamepadMode.DINPUT to "DirectInput"),
        selected = settings.mode,
        onSelect = { settings.mode = it },
        summary = stringResource(R.string.settings_gamepad_mode_summary)
    )
    if (settings.mode == GamepadMode.XINPUT) {
        val context = LocalContext.current
        val supported = remember { XusbSupport.isSupported(context) }
        SwitchSetting(
            title = stringResource(R.string.settings_gamepad_xbox360),
            summary = stringResource(if (supported) R.string.settings_gamepad_xbox360_summary else R.string.settings_gamepad_xbox360_unsupported),
            checked = supported && settings.xbox360Compat,
            onCheckedChange = { settings.xbox360Compat = it },
            enabled = supported
        )
    }
}
