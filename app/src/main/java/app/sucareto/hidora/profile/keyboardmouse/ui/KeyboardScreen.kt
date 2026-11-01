package app.sucareto.hidora.profile.keyboardmouse.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.sucareto.hidora.profile.keyboardmouse.KeyCodes
import app.sucareto.hidora.profile.keyboardmouse.KeyboardReporter
import app.sucareto.hidora.profile.keyboardmouse.model.ComboAction
import app.sucareto.hidora.profile.keyboardmouse.model.ComboKey
import app.sucareto.hidora.profile.keyboardmouse.model.DualKey
import app.sucareto.hidora.profile.keyboardmouse.model.FnKey
import app.sucareto.hidora.profile.keyboardmouse.model.KeyDef
import app.sucareto.hidora.profile.keyboardmouse.model.KeyboardLayout
import app.sucareto.hidora.profile.keyboardmouse.model.ModifierKey
import app.sucareto.hidora.profile.keyboardmouse.model.NormalKey
import app.sucareto.hidora.ui.components.PressHoldButton
import app.sucareto.hidora.ui.components.StickyState
import app.sucareto.hidora.ui.theme.Dimens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val FUNCTION_ROW_WEIGHT = 0.6f
private const val CTRL_ALT_DEL_PULSE_MS = 100L

private fun isGlyph(label: String) = label.codePointCount(0, label.length) == 1

private fun glyphSize(label: String) = if (isGlyph(label)) Dimens.KeyLegendFontSize else Dimens.KeyFontSize

private fun glyphWeight(label: String) = if (isGlyph(label)) FontWeight.Bold else FontWeight.Normal

private fun isFunctionKey(key: KeyDef) = when (key) {
    is ModifierKey, is FnKey -> true
    is ComboKey -> key.keycode != KeyCodes.SPACE && !isGlyph(key.label)
    else -> !isGlyph(key.label)
}

@Composable
fun KeyboardScreen(
    modifier: Modifier = Modifier,
    haptics: Boolean,
    capsLock: Boolean,
    onKeyStateChanged: (modifierBits: Int, pressedKeyCodes: Set<Int>) -> Unit,
    onSwitchToMouse: () -> Unit
) {
    val holder = rememberKeyboardStateHolder()
    val scope = rememberCoroutineScope()

    fun publish() = holder.publish(onKeyStateChanged)

    fun pulseCtrlAltDel() {
        scope.launch {
            holder.comboExtraModifierBits.intValue = KeyboardReporter.MODIFIER_LEFT_CTRL or KeyboardReporter.MODIFIER_LEFT_ALT
            holder.pressedKeys.value = holder.pressedKeys.value + KeyCodes.DELETE
            publish()
            delay(CTRL_ALT_DEL_PULSE_MS)
            holder.comboExtraModifierBits.intValue = 0
            holder.pressedKeys.value = holder.pressedKeys.value - KeyCodes.DELETE
            publish()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) holder.reset()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            holder.reset()
        }
    }

    val fnActive = holder.fnController.isActive
    val shiftActive = holder.modifierControllers[KeyboardReporter.MODIFIER_LEFT_SHIFT]?.isActive == true ||
        holder.modifierControllers[KeyboardReporter.MODIFIER_RIGHT_SHIFT]?.isActive == true

    Column(modifier = modifier.fillMaxSize().displayCutoutPadding()) {
        val rows = listOf(
            KeyboardLayout.functionRow to FUNCTION_ROW_WEIGHT,
            KeyboardLayout.numberRow to 1f,
            KeyboardLayout.qwertyRow to 1f,
            KeyboardLayout.homeRow to 1f,
            KeyboardLayout.bottomLettersRow to 1f,
            KeyboardLayout.controlRow to 1f
        )
        rows.forEach { (rowKeys, rowWeight) ->
            Row(modifier = Modifier.weight(rowWeight).fillMaxWidth()) {
                rowKeys.forEach { key ->
                    RenderKey(
                        key = key,
                        holder = holder,
                        fnActive = fnActive,
                        shiftActive = shiftActive,
                        capsLock = capsLock,
                        haptics = haptics,
                        onPublish = ::publish,
                        onSwitchToMouse = onSwitchToMouse,
                        onCtrlAltDelPulse = ::pulseCtrlAltDel
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.RenderKey(
    key: KeyDef,
    holder: KeyboardStateHolder,
    fnActive: Boolean,
    shiftActive: Boolean,
    capsLock: Boolean,
    haptics: Boolean,
    onPublish: () -> Unit,
    onSwitchToMouse: () -> Unit,
    onCtrlAltDelPulse: () -> Unit
) {
    val pressedKeys = holder.pressedKeys
    val fnController = holder.fnController
    val keyModifier = Modifier.weight(key.weight).fillMaxHeight().padding(Dimens.KeyGap)
    val functionKey = isFunctionKey(key)
    when (key) {
        is NormalKey -> PressHoldButton(
            label = key.label,
            fontSize = glyphSize(key.label),
            fontWeight = glyphWeight(key.label),
            shiftLabel = key.shiftLabel,
            shiftActive = shiftActive,
            modifier = keyModifier,
            functionKey = functionKey,
            highlightState = if (capsLock && key.keycode == KeyCodes.CAPS_LOCK) StickyState.LOCK else StickyState.OFF,
            haptic = haptics,
            onPress = { pressedKeys.value = pressedKeys.value + key.keycode; onPublish() },
            onRelease = {
                pressedKeys.value = pressedKeys.value - key.keycode
                onPublish()
            }
        )
        is DualKey -> {
            val pressedCode = remember { mutableIntStateOf(key.keycode) }
            val label = if (fnActive) key.fnLabel else key.label
            PressHoldButton(
                label = label,
                fontSize = glyphSize(label),
                fontWeight = glyphWeight(label),
                modifier = keyModifier,
                functionKey = functionKey,
                haptic = haptics,
                onPress = {
                    val code = if (fnController.isActive) key.fnKeycode else key.keycode
                    pressedCode.intValue = code
                    pressedKeys.value = pressedKeys.value + code
                    onPublish()
                },
                onRelease = {
                    pressedKeys.value = pressedKeys.value - pressedCode.intValue
                    onPublish()
                }
            )
        }
        is ModifierKey -> {
            val controller = holder.modifierControllers.getValue(key.bit)
            PressHoldButton(
                label = key.label,
                modifier = keyModifier,
                functionKey = functionKey,
                haptic = haptics,
                highlightState = controller.visualState,
                onPress = { controller.onPress(); onPublish() },
                onRelease = { controller.onRelease(); onPublish() }
            )
        }
        is FnKey -> PressHoldButton(
            label = key.label,
            modifier = keyModifier,
            functionKey = functionKey,
            haptic = haptics,
            highlightState = fnController.visualState,
            onPress = { fnController.onPress(); onPublish() },
            onRelease = { fnController.onRelease(); onPublish() }
        )
        is ComboKey -> {
            val tookFnAction = remember { mutableStateOf(false) }
            val fnLabel = key.fnLabelRes?.let { stringResource(it) }
            PressHoldButton(
                label = if (fnActive && fnLabel != null) fnLabel else key.label,
                modifier = keyModifier,
                functionKey = functionKey,
                haptic = haptics,
                onPress = {
                    val useFn = fnController.isActive
                    tookFnAction.value = useFn
                    if (useFn) {
                        when (key.action) {
                            ComboAction.SWITCH_TO_MOUSE -> onSwitchToMouse()
                            ComboAction.CTRL_ALT_DEL -> onCtrlAltDelPulse()
                        }
                    } else {
                        pressedKeys.value = pressedKeys.value + key.keycode
                        onPublish()
                    }
                },
                onRelease = {
                    if (!tookFnAction.value) {
                        pressedKeys.value = pressedKeys.value - key.keycode
                        onPublish()
                    }
                }
            )
        }
    }
}
