package app.sucareto.hidora.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import app.sucareto.hidora.ui.theme.Dimens
import app.sucareto.hidora.ui.theme.LocalAppExtraColors

@Composable
fun PressHoldSurface(
    modifier: Modifier = Modifier,
    haptic: Boolean = true,
    onPress: () -> Unit = {},
    onRelease: () -> Unit = {},
    content: @Composable BoxScope.(pressed: Boolean) -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val view = LocalView.current
    val press by rememberUpdatedState(onPress)
    val release by rememberUpdatedState(onRelease)
    val useHaptic by rememberUpdatedState(haptic)
    Box(
        modifier = modifier.pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                pressed = true
                if (useHaptic) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                press()
                try {
                    waitForUpOrCancellation()
                } finally {
                    pressed = false
                    release()
                }
            }
        },
        contentAlignment = Alignment.Center
    ) {
        content(pressed)
    }
}

private const val INACTIVE_LEGEND_ALPHA = 0.4f

@Composable
fun PressHoldButton(
    label: String,
    modifier: Modifier = Modifier,
    highlightState: StickyState = StickyState.OFF,
    fontSize: TextUnit = Dimens.KeyFontSize,
    fontWeight: FontWeight = FontWeight.Normal,
    functionKey: Boolean = false,
    shiftLabel: String? = null,
    shiftActive: Boolean = false,
    haptic: Boolean = true,
    onPress: () -> Unit = {},
    onRelease: () -> Unit = {}
) {
    val extraColors = LocalAppExtraColors.current
    PressHoldSurface(modifier = modifier, haptic = haptic, onPress = onPress, onRelease = onRelease) { pressed ->
        val background = when {
            highlightState == StickyState.LOCK -> extraColors.keyLocked
            highlightState == StickyState.HELD || pressed -> extraColors.keyHeld
            functionKey -> extraColors.keyFunction
            else -> extraColors.keyNormal
        }
        val foreground = when {
            highlightState == StickyState.LOCK -> extraColors.onKeyLocked
            highlightState == StickyState.HELD || pressed -> extraColors.onKeyHeld
            functionKey -> extraColors.onKeyFunction
            else -> extraColors.onKeyNormal
        }
        Box(Modifier.matchParentSize().background(background, RoundedCornerShape(Dimens.KeyCornerRadius)))
        if (shiftLabel == null) {
            Text(text = label, color = foreground, fontSize = fontSize, fontWeight = fontWeight, textAlign = TextAlign.Center)
        } else {
            val dimmed = foreground.copy(alpha = foreground.alpha * INACTIVE_LEGEND_ALPHA)
            Text(
                text = shiftLabel,
                color = if (shiftActive) foreground else dimmed,
                fontSize = Dimens.KeyLegendFontSize,
                fontWeight = if (shiftActive) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.align(Alignment.TopStart).padding(Dimens.KeyLegendPadding)
            )
            Text(
                text = label,
                color = if (shiftActive) dimmed else foreground,
                fontSize = Dimens.KeyLegendFontSize,
                fontWeight = if (shiftActive) FontWeight.Normal else FontWeight.Bold,
                modifier = Modifier.align(Alignment.BottomEnd).padding(Dimens.KeyLegendPadding)
            )
        }
    }
}
