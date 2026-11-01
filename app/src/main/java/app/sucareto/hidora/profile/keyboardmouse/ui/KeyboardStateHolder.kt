package app.sucareto.hidora.profile.keyboardmouse.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import app.sucareto.hidora.profile.keyboardmouse.model.KeyboardLayout
import app.sucareto.hidora.ui.components.StickyKeyController

class KeyboardStateHolder(
    val pressedKeys: MutableState<Set<Int>>,
    val fnController: StickyKeyController,
    val modifierControllers: Map<Int, StickyKeyController>,
    val comboExtraModifierBits: MutableIntState
) {
    fun publish(onKeyStateChanged: (modifierBits: Int, pressedKeyCodes: Set<Int>) -> Unit) {
        val bits = modifierControllers.filterValues { it.isActive }.keys
            .fold(comboExtraModifierBits.intValue) { acc, bit -> acc or bit }
        onKeyStateChanged(bits, pressedKeys.value)
    }

    fun reset() {
        fnController.reset()
        modifierControllers.values.forEach { it.reset() }
        pressedKeys.value = emptySet()
        comboExtraModifierBits.intValue = 0
    }
}

@Composable
fun rememberKeyboardStateHolder(): KeyboardStateHolder {
    val pressedKeys = remember { mutableStateOf(setOf<Int>()) }
    val fnController = remember { StickyKeyController() }
    val modifierControllers = remember {
        KeyboardLayout.allModifierBits.associateWith { StickyKeyController() }
    }
    val comboExtraModifierBits = remember { mutableIntStateOf(0) }
    return remember(pressedKeys, fnController, modifierControllers, comboExtraModifierBits) {
        KeyboardStateHolder(pressedKeys, fnController, modifierControllers, comboExtraModifierBits)
    }
}
