package app.sucareto.hidora.profile.keyboardmouse

import app.sucareto.hidora.core.hid.HidEndpoints

class KeyboardMouseOutput(endpoints: HidEndpoints) {
    private val lock = Any()
    private val keyboardBoot = endpoints[KeyboardMouseProfile.FUNCTION_KEYBOARD_BOOT]
    private val keyboardNkro = endpoints[KeyboardMouseProfile.FUNCTION_KEYBOARD_NKRO]
    private val mouse = endpoints[KeyboardMouseProfile.FUNCTION_MOUSE]
    private var mouseButtonsBits = 0
    private var keyModifier = 0
    private var keyCodes: Set<Int> = emptySet()
    private var keysStale = false
    private var mouseButtonsStale = false

    fun writeKeyState(modifier: Int, pressedKeyCodes: Set<Int>): Result<Unit> = synchronized(lock) {
        keyModifier = modifier
        keyCodes = pressedKeyCodes
        val result = writeKeys()
        if (result.isFailure && pressedKeyCodes.isNotEmpty()) {
            keyboardBoot.write(KeyboardReporter.buildRelease())
            keyboardNkro.write(KeyboardReporter.buildNkroReport(0, emptySet()))
        }
        result
    }

    fun moveMouse(dx: Int, dy: Int): Result<Unit> = synchronized(lock) {
        mouse.write(MouseReporter.buildReport(mouseButtonsBits, dx, dy, 0))
    }

    fun scrollMouse(delta: Int): Result<Unit> = synchronized(lock) {
        mouse.write(MouseReporter.buildReport(mouseButtonsBits, 0, 0, delta))
    }

    fun setMouseButton(bit: Int, pressed: Boolean): Result<Unit> = synchronized(lock) {
        mouseButtonsBits = if (pressed) mouseButtonsBits or bit else mouseButtonsBits and bit.inv()
        writeMouseButtons()
    }

    fun resendIfStale(): Result<Unit> = synchronized(lock) {
        val keys = if (keysStale) writeKeys() else Result.success(Unit)
        val buttons = if (mouseButtonsStale) writeMouseButtons() else Result.success(Unit)
        keys.and(buttons)
    }

    fun readLedReport(buffer: ByteArray): Result<Int> = keyboardBoot.read(buffer)

    fun awaitLedReport(timeoutMs: Int): Result<Boolean> = keyboardBoot.awaitReadable(timeoutMs)

    fun releaseAll(): Result<Unit> = synchronized(lock) {
        keyModifier = 0
        keyCodes = emptySet()
        mouseButtonsBits = 0
        writeKeys().and(writeMouseButtons())
    }

    private fun writeKeys(): Result<Unit> {
        val boot = keyboardBoot.write(KeyboardReporter.buildReport(keyModifier, keyCodes.toList()))
        val nkro = keyboardNkro.write(KeyboardReporter.buildNkroReport(keyModifier, keyCodes))
        return boot.and(nkro).also { keysStale = it.isFailure }
    }

    private fun writeMouseButtons(): Result<Unit> =
        mouse.write(MouseReporter.buildReport(mouseButtonsBits, 0, 0, 0)).also { mouseButtonsStale = it.isFailure }

    private fun Result<Unit>.and(other: Result<Unit>): Result<Unit> = if (isFailure) this else other
}
