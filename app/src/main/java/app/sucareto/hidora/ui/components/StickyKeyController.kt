package app.sucareto.hidora.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class StickyState { OFF, HELD, LOCK }

class StickyKeyController {
    var locked by mutableStateOf(false)
        private set
    var held by mutableStateOf(false)
        private set

    private var lastReleaseTimeMs = 0L

    val isActive: Boolean get() = locked || held

    val visualState: StickyState
        get() = when {
            locked -> StickyState.LOCK
            held -> StickyState.HELD
            else -> StickyState.OFF
        }

    fun onPress() {
        held = true
        val now = System.currentTimeMillis()
        if (now - lastReleaseTimeMs <= DOUBLE_TAP_MS) locked = !locked
    }

    fun onRelease() {
        held = false
        lastReleaseTimeMs = System.currentTimeMillis()
    }

    fun reset() {
        locked = false
        held = false
    }

    companion object {
        private const val DOUBLE_TAP_MS = 350L
    }
}
