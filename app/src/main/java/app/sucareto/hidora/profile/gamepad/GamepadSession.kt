package app.sucareto.hidora.profile.gamepad

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import app.sucareto.hidora.core.hid.ResendLoop
import app.sucareto.hidora.core.profile.ProfileSession
import app.sucareto.hidora.profile.gamepad.ui.GamepadCallbacks
import app.sucareto.hidora.profile.gamepad.ui.GamepadScreen
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class GamepadSession(private val output: GamepadOutput, private val settings: GamepadSettings) : ProfileSession {
    private val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    @Volatile private var leftStick = Offset.Zero
    @Volatile private var rightStick = Offset.Zero
    @Volatile private var leftTrigger = 0
    @Volatile private var rightTrigger = 0
    private val analogPending = AtomicBoolean(false)
    private val resendLoop = ResendLoop(writeScope) { output.resendIfStale() }

    @Composable
    override fun Content(onLeave: () -> Unit) {
        GamepadScreen(
            callbacks = GamepadCallbacks(
                onButton = { bit, pressed -> write { setButton(bit, pressed) } },
                onHat = { hat -> write { setHat(hat) } },
                onTrigger = ::updateTrigger,
                onStick = ::updateStick,
                onLeave = onLeave
            ),
            style = settings.uiStyle()
        )
    }

    override fun releaseInputs() {
        leftStick = Offset.Zero
        rightStick = Offset.Zero
        leftTrigger = 0
        rightTrigger = 0
        if (output.releaseAll().isFailure) resendLoop.request()
    }

    override fun close() {
        writeScope.cancel()
        output.releaseAll()
    }

    private fun updateStick(left: Boolean, position: Offset) {
        if (left) leftStick = position else rightStick = position
        requestAnalog(conflate = true)
    }

    private fun updateTrigger(left: Boolean, value: Int) {
        val previous = if (left) leftTrigger else rightTrigger
        if (left) leftTrigger = value else rightTrigger = value
        requestAnalog(conflate = (previous == 0) == (value == 0))
    }

    private fun requestAnalog(conflate: Boolean) {
        if (!conflate) {
            write { flushAnalog() }
        } else if (analogPending.compareAndSet(false, true)) {
            write {
                analogPending.set(false)
                flushAnalog()
            }
        }
    }

    private fun GamepadOutput.flushAnalog() = setAnalog(leftStick, rightStick, leftTrigger, rightTrigger)

    private fun write(block: GamepadOutput.() -> Result<Unit>) {
        writeScope.launch { if (output.block().isFailure) resendLoop.request() }
    }
}
