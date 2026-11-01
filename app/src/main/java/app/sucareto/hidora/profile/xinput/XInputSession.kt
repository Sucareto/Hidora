package app.sucareto.hidora.profile.xinput

import androidx.compose.runtime.Composable
import app.sucareto.hidora.core.profile.ProfileSession
import app.sucareto.hidora.profile.gamepad.GamepadSettings
import app.sucareto.hidora.profile.gamepad.ui.GamepadCallbacks
import app.sucareto.hidora.profile.gamepad.ui.GamepadScreen

class XInputSession(private val output: PadOutput, private val settings: GamepadSettings, private val share: Boolean = false) : ProfileSession {
    @Composable
    override fun Content(onLeave: () -> Unit) {
        GamepadScreen(
            callbacks = GamepadCallbacks(
                onButton = output::setButton,
                onHat = output::setHat,
                onTrigger = output::setTrigger,
                onStick = output::setStick,
                onLeave = onLeave
            ),
            style = settings.uiStyle(share)
        )
    }

    override fun releaseInputs() = output.releaseAll()

    override fun close() = output.close()
}
