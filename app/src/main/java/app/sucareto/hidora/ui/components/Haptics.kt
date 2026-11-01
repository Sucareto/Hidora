package app.sucareto.hidora.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalView

@Composable
fun hapticClick(enabled: Boolean, onClick: () -> Unit): () -> Unit {
    val view = LocalView.current
    return {
        if (enabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        onClick()
    }
}
