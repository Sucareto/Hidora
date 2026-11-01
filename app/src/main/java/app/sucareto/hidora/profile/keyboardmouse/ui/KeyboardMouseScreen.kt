package app.sucareto.hidora.profile.keyboardmouse.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.sucareto.hidora.profile.keyboardmouse.MouseReporter
import app.sucareto.hidora.profile.keyboardmouse.MouseSpeed

private enum class SubView { KEYBOARD, MOUSE }

data class KeyboardMouseUiState(val speed: MouseSpeed, val buttonsOnLeft: Boolean, val startOnMouse: Boolean, val keyHaptics: Boolean, val capsLock: Boolean)

data class KeyboardMouseCallbacks(
    val onKeyStateChanged: (modifierBits: Int, pressedKeyCodes: Set<Int>) -> Unit,
    val onMouseMove: (dx: Int, dy: Int) -> Unit,
    val onMouseWheel: (delta: Int) -> Unit,
    val onMouseButtonChange: (bit: Int, pressed: Boolean) -> Unit,
    val onSpeedCycle: () -> Unit,
    val onSwapHand: () -> Unit,
    val onReleaseAll: () -> Unit,
    val onLeave: () -> Unit
)

@Composable
fun KeyboardMouseScreen(
    state: KeyboardMouseUiState,
    callbacks: KeyboardMouseCallbacks,
    modifier: Modifier = Modifier
) {
    var subView by rememberSaveable { mutableStateOf(if (state.startOnMouse) SubView.MOUSE else SubView.KEYBOARD) }

    BackHandler(onBack = callbacks.onLeave)

    fun switchTo(next: SubView) {
        callbacks.onReleaseAll()
        subView = next
    }

    when (subView) {
        SubView.KEYBOARD -> KeyboardScreen(
            modifier = modifier.fillMaxSize(),
            haptics = state.keyHaptics,
            capsLock = state.capsLock,
            onKeyStateChanged = callbacks.onKeyStateChanged,
            onSwitchToMouse = { switchTo(SubView.MOUSE) }
        )
        SubView.MOUSE -> MouseScreen(
            state = state,
            callbacks = callbacks,
            onSwitchToKeyboard = { switchTo(SubView.KEYBOARD) }
        )
    }
}

@Composable
private fun MouseScreen(
    state: KeyboardMouseUiState,
    callbacks: KeyboardMouseCallbacks,
    onSwitchToKeyboard: () -> Unit
) {
    Row(modifier = Modifier.fillMaxSize().displayCutoutPadding()) {
        val touchpad: @Composable (Modifier) -> Unit = { w ->
            Trackpad(
                modifier = w,
                baseGain = state.speed.gain,
                onMove = callbacks.onMouseMove,
                onWheel = callbacks.onMouseWheel
            )
        }
        val buttonColumn: @Composable (Modifier) -> Unit = { w ->
            MouseButtonColumn(
                modifier = w,
                speed = state.speed,
                haptics = state.keyHaptics,
                onSpeedCycle = callbacks.onSpeedCycle,
                onSwapHand = callbacks.onSwapHand,
                onSwitchToKeyboard = onSwitchToKeyboard,
                onLeftButtonChange = { pressed -> callbacks.onMouseButtonChange(MouseReporter.BUTTON_LEFT, pressed) },
                onMiddleButtonChange = { pressed -> callbacks.onMouseButtonChange(MouseReporter.BUTTON_MIDDLE, pressed) },
                onRightButtonChange = { pressed -> callbacks.onMouseButtonChange(MouseReporter.BUTTON_RIGHT, pressed) }
            )
        }
        if (state.buttonsOnLeft) {
            buttonColumn(Modifier.weight(0.45f))
            touchpad(Modifier.weight(0.55f))
        } else {
            touchpad(Modifier.weight(0.55f))
            buttonColumn(Modifier.weight(0.45f))
        }
    }
}
