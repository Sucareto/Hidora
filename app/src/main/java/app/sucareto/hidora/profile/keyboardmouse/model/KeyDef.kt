package app.sucareto.hidora.profile.keyboardmouse.model

import androidx.annotation.StringRes

sealed interface KeyDef {
    val label: String
    val weight: Float
}

data class NormalKey(
    val keycode: Int,
    override val label: String,
    override val weight: Float = 1f,
    val shiftLabel: String? = null
) : KeyDef

data class DualKey(
    val keycode: Int,
    val fnKeycode: Int,
    override val label: String,
    val fnLabel: String,
    override val weight: Float = 1f
) : KeyDef

data class ModifierKey(val bit: Int, override val label: String, override val weight: Float = 1f) : KeyDef

data class FnKey(override val label: String = "Fn", override val weight: Float = 1f) : KeyDef

enum class ComboAction { SWITCH_TO_MOUSE, CTRL_ALT_DEL }

data class ComboKey(
    val keycode: Int,
    val action: ComboAction,
    override val label: String,
    override val weight: Float = 1f,
    @StringRes val fnLabelRes: Int? = null
) : KeyDef
