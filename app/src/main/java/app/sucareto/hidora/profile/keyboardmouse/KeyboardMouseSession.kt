package app.sucareto.hidora.profile.keyboardmouse

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.sucareto.hidora.core.hid.ResendLoop
import app.sucareto.hidora.core.profile.ProfileSession
import app.sucareto.hidora.profile.keyboardmouse.ui.KeyboardMouseCallbacks
import app.sucareto.hidora.profile.keyboardmouse.ui.KeyboardMouseScreen
import app.sucareto.hidora.profile.keyboardmouse.ui.KeyboardMouseUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class KeyboardMouseSession(
    private val output: KeyboardMouseOutput,
    private val settings: KeyboardMouseSettings
) : ProfileSession {
    private val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val ledScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var leds by mutableIntStateOf(0)
    private val resendLoop = ResendLoop(writeScope) { output.resendIfStale() }

    init {
        ledScope.launch { watchLeds() }
    }

    @Composable
    override fun Content(onLeave: () -> Unit) {
        var speed by remember { mutableStateOf(settings.mouseSpeed) }
        var buttonsOnLeft by remember { mutableStateOf(settings.buttonsOnLeft) }
        KeyboardMouseScreen(
            state = KeyboardMouseUiState(
                speed = speed,
                buttonsOnLeft = buttonsOnLeft,
                startOnMouse = settings.startPage == StartPage.MOUSE,
                keyHaptics = settings.keyHaptics,
                capsLock = (leds and KeyboardReporter.LED_CAPS_LOCK) != 0
            ),
            callbacks = KeyboardMouseCallbacks(
                onKeyStateChanged = { modifierBits, keys -> write { writeKeyState(modifierBits, keys) } },
                onMouseMove = { dx, dy -> write { moveMouse(dx, dy) } },
                onMouseWheel = { delta -> write { scrollMouse(delta) } },
                onMouseButtonChange = { bit, pressed -> write { setMouseButton(bit, pressed) } },
                onSpeedCycle = { speed = speed.next() },
                onSwapHand = { buttonsOnLeft = !buttonsOnLeft },
                onReleaseAll = { write { releaseAll() } },
                onLeave = onLeave
            )
        )
    }

    override fun releaseInputs() {
        if (output.releaseAll().isFailure) resendLoop.request()
    }

    override fun close() {
        ledScope.cancel()
        writeScope.cancel()
        output.releaseAll()
    }

    private fun CoroutineScope.watchLeds() {
        val buffer = ByteArray(KeyboardMouseDescriptors.BOOT_REPORT_LENGTH)
        while (isActive) {
            val n = output.readLedReport(buffer).getOrElse { return }
            if (n > 0) {
                leds = buffer[0].toInt() and 0xFF
                continue
            }
            output.awaitLedReport(LED_POLL_TIMEOUT_MS).getOrElse { return }
        }
    }

    private fun write(block: KeyboardMouseOutput.() -> Result<Unit>) {
        writeScope.launch { if (output.block().isFailure) resendLoop.request() }
    }

    private companion object {
        const val LED_POLL_TIMEOUT_MS = 1000
    }
}
