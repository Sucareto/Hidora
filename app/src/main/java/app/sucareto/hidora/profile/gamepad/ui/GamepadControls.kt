package app.sucareto.hidora.profile.gamepad.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import app.sucareto.hidora.profile.gamepad.GamepadReport
import app.sucareto.hidora.ui.components.PressHoldSurface
import app.sucareto.hidora.ui.theme.LocalAppExtraColors
import kotlin.math.atan2
import kotlin.math.roundToInt

private const val STICK_TRAVEL = 0.72f
private const val STICK_KNOB = 0.24f
private const val DPAD_DEAD_ZONE = 0.22f
private const val DPAD_ARM_WIDTH = 0.34f
private const val DPAD_ARM_LENGTH = 0.86f
private const val TRIGGER_MIN_DEPTH = 0.10f
private const val TRIGGER_FULL_AT = 0.80f

object GamepadColors {
    val FaceButton = Color(0xFF202225)
    val FaceButtonPressed = Color(0xFF4A4E55)
    val LetterA = Color(0xFF6CC644)
    val LetterB = Color(0xFFEF4B45)
    val LetterX = Color(0xFF3D8FE0)
    val LetterY = Color(0xFFF2C12E)
}

@Composable
fun GamepadKey(
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    label: String? = null,
    labelColor: Color? = null,
    fontSize: TextUnit = TextUnit.Unspecified,
    face: Boolean = false,
    icon: (DrawScope.(Color) -> Unit)? = null,
    haptic: Boolean = false,
    onPress: () -> Unit,
    onRelease: () -> Unit
) {
    val colors = LocalAppExtraColors.current
    PressHoldSurface(modifier = modifier, haptic = haptic, onPress = onPress, onRelease = onRelease) { pressed ->
        val background = when {
            face && pressed -> GamepadColors.FaceButtonPressed
            face -> GamepadColors.FaceButton
            pressed -> colors.keyHeld
            else -> colors.keyNormal
        }
        val foreground = labelColor ?: if (pressed) colors.onKeyHeld else colors.onKeyNormal
        Box(Modifier.matchParentSize().background(background, shape))
        if (icon != null) Canvas(Modifier.fillMaxSize()) { icon(foreground) }
        if (label != null) Text(text = label, color = foreground, fontSize = fontSize, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun TriggerBar(modifier: Modifier = Modifier, label: String, deepensLeftward: Boolean, hair: Boolean, haptic: Boolean = false, onValueChange: (Int) -> Unit) {
    val change by rememberUpdatedState(onValueChange)
    var value by remember { mutableIntStateOf(0) }
    val colors = LocalAppExtraColors.current
    val shape = RoundedCornerShape(percent = 50)
    val leftward by rememberUpdatedState(deepensLeftward)
    val hairTrigger by rememberUpdatedState(hair)
    val useHaptic by rememberUpdatedState(haptic)
    val view = LocalView.current
    Box(
        modifier = modifier
            .clip(shape)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (useHaptic) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    val hairMode = hairTrigger
                    fun update(position: Offset) {
                        val travel = if (leftward) size.width - position.x else position.x
                        val depth = (travel / (size.width * TRIGGER_FULL_AT)).coerceIn(TRIGGER_MIN_DEPTH, 1f)
                        val next = if (hairMode) GamepadReport.TRIGGER_MAX else (depth * GamepadReport.TRIGGER_MAX).roundToInt()
                        if (next != value) {
                            value = next
                            change(next)
                        }
                    }
                    try {
                        update(down.position)
                        trackPointer(down.id) { update(it) }
                    } finally {
                        value = 0
                        change(0)
                    }
                }
            }
            .drawBehind {
                drawRect(colors.keyNormal)
                val fill = size.width * value / GamepadReport.TRIGGER_MAX
                val left = if (leftward) size.width - fill else 0f
                drawRect(colors.keyHeld, Offset(left, 0f), Size(fill, size.height))
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (value > GamepadReport.TRIGGER_MAX / 2) colors.onKeyHeld else colors.onKeyNormal,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun VirtualStick(modifier: Modifier = Modifier, onMove: (Offset) -> Unit) {
    val move by rememberUpdatedState(onMove)
    var knob by remember { mutableStateOf(Offset.Zero) }
    var active by remember { mutableStateOf(false) }
    val colors = LocalAppExtraColors.current
    Box(
        modifier = modifier
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val travel = size.width / 2f * STICK_TRAVEL
                    fun update(position: Offset) {
                        val raw = (position - center) / travel
                        val distance = raw.getDistance()
                        val clamped = if (distance > 1f) raw / distance else raw
                        if (clamped != knob) {
                            knob = clamped
                            move(clamped)
                        }
                    }
                    active = true
                    try {
                        update(down.position)
                        trackPointer(down.id) { update(it) }
                    } finally {
                        active = false
                        knob = Offset.Zero
                        move(Offset.Zero)
                    }
                }
            }
            .drawBehind {
                val radius = size.minDimension / 2f
                drawCircle(colors.keyNormal, radius)
                drawCircle(colors.trackpadBorder, radius, style = Stroke(2.dp.toPx()))
                val knobCenter = center + knob * (radius * STICK_TRAVEL)
                drawCircle(
                    if (active) colors.keyHeld else colors.onKeyNormal.copy(alpha = 0.7f),
                    radius * STICK_KNOB * 2f,
                    knobCenter
                )
            }
    )
}

@Composable
fun DPad(modifier: Modifier = Modifier, haptic: Boolean = false, onHatChange: (Int) -> Unit) {
    val change by rememberUpdatedState(onHatChange)
    var hat by remember { mutableIntStateOf(GamepadReport.HAT_NONE) }
    val useHaptic by rememberUpdatedState(haptic)
    val view = LocalView.current
    val colors = LocalAppExtraColors.current
    Box(
        modifier = modifier
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val radius = size.width / 2f
                    fun update(position: Offset) {
                        val next = hatAt((position - center) / radius)
                        if (next != hat) {
                            if (useHaptic && next != GamepadReport.HAT_NONE) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            hat = next
                            change(next)
                        }
                    }
                    try {
                        update(down.position)
                        trackPointer(down.id) { update(it) }
                    } finally {
                        if (hat != GamepadReport.HAT_NONE) {
                            hat = GamepadReport.HAT_NONE
                            change(GamepadReport.HAT_NONE)
                        }
                    }
                }
            }
            .drawBehind {
                val d = size.minDimension
                drawCircle(colors.keyNormal, d / 2f)
                drawCircle(colors.trackpadBorder, d / 2f, style = Stroke(2.dp.toPx()))
                val arm = d * DPAD_ARM_WIDTH
                val half = d * DPAD_ARM_LENGTH / 2f
                val idle = colors.onKeyNormal.copy(alpha = 0.55f).compositeOver(colors.keyNormal)
                val corner = CornerRadius(arm * 0.18f)
                fun armRect(color: Color, topLeft: Offset, armSize: Size) =
                    drawRoundRect(color, topLeft, armSize, corner)
                armRect(idle, Offset(center.x - arm / 2f, center.y - half), Size(arm, half * 2f))
                armRect(idle, Offset(center.x - half, center.y - arm / 2f), Size(half * 2f, arm))
                val held = colors.keyHeld
                val armLength = half - arm / 2f
                if (hat in UP_HATS) armRect(held, Offset(center.x - arm / 2f, center.y - half), Size(arm, armLength))
                if (hat in DOWN_HATS) armRect(held, Offset(center.x - arm / 2f, center.y + arm / 2f), Size(arm, armLength))
                if (hat in LEFT_HATS) armRect(held, Offset(center.x - half, center.y - arm / 2f), Size(armLength, arm))
                if (hat in RIGHT_HATS) armRect(held, Offset(center.x + arm / 2f, center.y - arm / 2f), Size(armLength, arm))
            }
    )
}

private val UP_HATS = setOf(GamepadReport.HAT_UP_LEFT, GamepadReport.HAT_UP, GamepadReport.HAT_UP_RIGHT)
private val RIGHT_HATS = setOf(GamepadReport.HAT_UP_RIGHT, GamepadReport.HAT_RIGHT, GamepadReport.HAT_DOWN_RIGHT)
private val DOWN_HATS = setOf(GamepadReport.HAT_DOWN_RIGHT, GamepadReport.HAT_DOWN, GamepadReport.HAT_DOWN_LEFT)
private val LEFT_HATS = setOf(GamepadReport.HAT_DOWN_LEFT, GamepadReport.HAT_LEFT, GamepadReport.HAT_UP_LEFT)

private fun hatAt(normalized: Offset): Int {
    if (normalized.getDistance() < DPAD_DEAD_ZONE) return GamepadReport.HAT_NONE
    val degrees = Math.toDegrees(atan2(normalized.x, -normalized.y).toDouble())
    return (((degrees + 360.0 + 22.5) / 45.0).toInt()) % 8
}

private suspend fun AwaitPointerEventScope.trackPointer(id: PointerId, onPosition: (Offset) -> Unit) {
    while (true) {
        val change = awaitPointerEvent().changes.firstOrNull { it.id == id } ?: return
        if (!change.pressed) return
        onPosition(change.position)
        change.consume()
    }
}

fun DrawScope.viewIcon(color: Color) {
    val s = size.minDimension * 0.22f
    val stroke = Stroke(size.minDimension * 0.06f)
    val corner = CornerRadius(s * 0.2f)
    drawRoundRect(color, Offset(center.x - s * 0.75f, center.y - s * 0.75f), Size(s, s), corner, style = stroke)
    drawRoundRect(color, Offset(center.x - s * 0.25f, center.y - s * 0.25f), Size(s, s), corner, style = stroke)
}

fun DrawScope.menuIcon(color: Color) {
    val w = size.minDimension * 0.36f
    val strokeWidth = size.minDimension * 0.06f
    for (i in -1..1) {
        val y = center.y + i * size.minDimension * 0.12f
        drawLine(color, Offset(center.x - w / 2f, y), Offset(center.x + w / 2f, y), strokeWidth)
    }
}

fun DrawScope.guideIcon(color: Color) {
    drawCircle(color, size.minDimension * 0.24f, style = Stroke(size.minDimension * 0.07f))
    drawCircle(color, size.minDimension * 0.08f)
}
