package app.sucareto.hidora.profile.keyboardmouse.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.sucareto.hidora.R
import app.sucareto.hidora.profile.keyboardmouse.MouseSpeed
import app.sucareto.hidora.ui.components.PressHoldButton
import app.sucareto.hidora.ui.components.hapticClick
import app.sucareto.hidora.ui.theme.Dimens

@Composable
fun MouseButtonColumn(
    modifier: Modifier = Modifier,
    speed: MouseSpeed,
    haptics: Boolean,
    onSpeedCycle: () -> Unit,
    onSwapHand: () -> Unit,
    onSwitchToKeyboard: () -> Unit,
    onLeftButtonChange: (pressed: Boolean) -> Unit,
    onMiddleButtonChange: (pressed: Boolean) -> Unit,
    onRightButtonChange: (pressed: Boolean) -> Unit
) {
    Column(
        modifier = modifier.fillMaxHeight().padding(Dimens.ColumnPadding),
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        PressHoldButton(
            label = stringResource(R.string.mouse_button_left),
            modifier = Modifier.weight(1f).fillMaxWidth().padding(Dimens.ButtonSpacing),
            haptic = haptics,
            onPress = { onLeftButtonChange(true) },
            onRelease = { onLeftButtonChange(false) }
        )
        PressHoldButton(
            label = stringResource(R.string.mouse_button_middle),
            modifier = Modifier.weight(1f).fillMaxWidth().padding(Dimens.ButtonSpacing),
            haptic = haptics,
            onPress = { onMiddleButtonChange(true) },
            onRelease = { onMiddleButtonChange(false) }
        )
        PressHoldButton(
            label = stringResource(R.string.mouse_button_right),
            modifier = Modifier.weight(1f).fillMaxWidth().padding(Dimens.ButtonSpacing),
            haptic = haptics,
            onPress = { onRightButtonChange(true) },
            onRelease = { onRightButtonChange(false) }
        )
        FilledTonalButton(
            onClick = hapticClick(haptics, onSpeedCycle),
            modifier = Modifier.fillMaxWidth().padding(Dimens.ButtonSpacing)
        ) {
            Text(stringResource(speed.labelRes))
        }
        FilledTonalButton(
            onClick = hapticClick(haptics, onSwapHand),
            modifier = Modifier.fillMaxWidth().padding(Dimens.ButtonSpacing)
        ) {
            Text(stringResource(R.string.mouse_swap_hand))
        }
        FilledTonalButton(
            onClick = hapticClick(haptics, onSwitchToKeyboard),
            modifier = Modifier.fillMaxWidth().padding(Dimens.ButtonSpacing)
        ) {
            Text(stringResource(R.string.mouse_switch_keyboard))
        }
    }
}
