package app.sucareto.hidora.profile.keyboardmouse

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.sucareto.hidora.R
import app.sucareto.hidora.core.profile.PrefsStore
import app.sucareto.hidora.ui.settings.ChoiceSetting
import app.sucareto.hidora.ui.settings.SwitchSetting

enum class StartPage { KEYBOARD, MOUSE }

class KeyboardMouseSettings private constructor(context: Context) : PrefsStore(context, "keyboard_mouse_settings") {
    var startPage by enum("start_page", StartPage.KEYBOARD)
    var keyHaptics by boolean("key_haptics", true)
    var mouseSpeed by enum("mouse_speed", MouseSpeed.DEFAULT)
    var buttonsOnLeft by boolean("buttons_on_left", true)

    companion object {
        @Volatile private var instance: KeyboardMouseSettings? = null

        fun get(context: Context): KeyboardMouseSettings =
            instance ?: synchronized(this) { instance ?: KeyboardMouseSettings(context).also { instance = it } }
    }
}

@Composable
fun KeyboardMouseSettingsContent(settings: KeyboardMouseSettings) {
    ChoiceSetting(
        title = stringResource(R.string.settings_km_start_page),
        options = listOf(StartPage.KEYBOARD to stringResource(R.string.mouse_switch_keyboard), StartPage.MOUSE to stringResource(R.string.fn_label_mouse)),
        selected = settings.startPage,
        onSelect = { settings.startPage = it }
    )
    ChoiceSetting(
        title = stringResource(R.string.settings_mouse_speed),
        options = listOf(
            MouseSpeed.LOW to stringResource(R.string.settings_speed_low),
            MouseSpeed.MEDIUM to stringResource(R.string.settings_speed_mid),
            MouseSpeed.HIGH to stringResource(R.string.settings_speed_high)
        ),
        selected = settings.mouseSpeed,
        onSelect = { settings.mouseSpeed = it }
    )
    ChoiceSetting(
        title = stringResource(R.string.settings_mouse_buttons_side),
        options = listOf(true to stringResource(R.string.settings_side_left), false to stringResource(R.string.settings_side_right)),
        selected = settings.buttonsOnLeft,
        onSelect = { settings.buttonsOnLeft = it },
        summary = stringResource(R.string.settings_mouse_defaults_summary)
    )
    SwitchSetting(
        title = stringResource(R.string.settings_key_haptics),
        checked = settings.keyHaptics,
        onCheckedChange = { settings.keyHaptics = it }
    )
}
