package app.sucareto.hidora.profile.gamepad

import androidx.compose.ui.geometry.Offset
import app.sucareto.hidora.core.hid.HidEndpoint

class GamepadOutput(private val endpoint: HidEndpoint) {
    private val lock = Any()
    private val state = GamepadState()
    private var stale = false

    fun setButton(bit: Int, pressed: Boolean): Result<Unit> = writeHeld {
        buttons = if (pressed) buttons or bit else buttons and bit.inv()
    }

    fun setHat(hat: Int): Result<Unit> = writeHeld { this.hat = hat }

    fun setAnalog(left: Offset, right: Offset, leftTrigger: Int, rightTrigger: Int): Result<Unit> = writeHeld {
        leftX = left.x
        leftY = left.y
        rightX = right.x
        rightY = right.y
        this.leftTrigger = leftTrigger
        this.rightTrigger = rightTrigger
    }

    fun resendIfStale(): Result<Unit> = synchronized(lock) {
        if (!stale) return@synchronized Result.success(Unit)
        endpoint.write(GamepadReport.build(state)).also { stale = it.isFailure }
    }

    fun releaseAll(): Result<Unit> = synchronized(lock) {
        state.reset()
        endpoint.write(GamepadReport.buildNeutral()).also { stale = it.isFailure }
    }

    private fun writeHeld(block: GamepadState.() -> Unit): Result<Unit> = synchronized(lock) {
        state.block()
        val result = endpoint.write(GamepadReport.build(state))
        stale = result.isFailure
        if (result.isFailure && state.hasHeldInputs) endpoint.write(GamepadReport.buildNeutral())
        result
    }
}
