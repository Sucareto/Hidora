package app.sucareto.hidora.profile.gamepad.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import app.sucareto.hidora.profile.gamepad.GamepadReport
import app.sucareto.hidora.profile.gamepad.LabelStyle

data class GamepadCallbacks(
    val onButton: (bit: Int, pressed: Boolean) -> Unit,
    val onHat: (hat: Int) -> Unit,
    val onTrigger: (left: Boolean, value: Int) -> Unit,
    val onStick: (left: Boolean, position: Offset) -> Unit,
    val onLeave: () -> Unit
)

private object NanoLayout {
    const val STICK = 0.40f
    const val LEFT_STICK_X = 0.15f
    const val LEFT_STICK_Y = 0.46f
    const val RIGHT_STICK_X = 0.667f
    const val RIGHT_STICK_Y = 0.75f
    const val DPAD = 0.40f
    const val DPAD_X = 0.33f
    const val DPAD_Y = 0.75f
    const val FACE = 0.15f
    const val FACE_X = 0.854f
    const val FACE_Y = 0.46f
    const val FACE_DX = 0.0775f
    const val FACE_DY = 0.14f
    const val GUIDE = 0.14f
    const val GUIDE_Y = 0.30f
    const val VIEW_MENU = 0.11f
    const val VIEW_X = 0.396f
    const val MENU_X = 0.604f
    const val VIEW_MENU_Y = GUIDE_Y
    const val SERIES_GUIDE_Y = 0.25f
    const val SERIES_VIEW_MENU_Y = 0.34f
    const val SERIES_SHARE_Y = 0.43f
    const val STICK_CLICK = 0.14f
    const val STICK_CLICK_Y = 0.88f
    const val LS_X = 0.06f
    const val RS_X = 0.94f
    const val SHOULDER_W = 0.14f
    const val SHOULDER_H = 0.13f
    const val SHOULDER_Y = 0.08f
    const val TRIGGER_W = 0.28f
    const val OUTER_X = 0.08f
    const val INNER_X = 0.32f
    const val SWAPPED_TRIGGER_X = 0.15f
    const val SWAPPED_BUMPER_X = 0.37f
}

data class GamepadUiStyle(
    val labels: LabelStyle,
    val symmetric: Boolean,
    val haptics: Boolean,
    val swapShoulders: Boolean,
    val hairTriggers: Boolean,
    val share: Boolean
)

@Composable
fun GamepadScreen(callbacks: GamepadCallbacks, style: GamepadUiStyle, modifier: Modifier = Modifier) {
    BackHandler(onBack = callbacks.onLeave)
    val labels = labelsFor(style.labels)

    BoxWithConstraints(modifier = modifier.fillMaxSize().displayCutoutPadding()) {
        val w = maxWidth
        val h = maxHeight
        fun at(cx: Float, cy: Float, width: Dp, height: Dp = width) =
            Modifier.offset(x = w * cx - width / 2, y = h * cy - height / 2).size(width, height)

        @Composable
        fun Key(
            modifier: Modifier,
            bit: Int,
            label: String? = null,
            face: Boolean = false,
            labelColor: Color? = null,
            shape: Shape = CircleShape,
            icon: (DrawScope.(Color) -> Unit)? = null
        ) {
            GamepadKey(
                modifier = modifier,
                shape = shape,
                label = label,
                labelColor = labelColor,
                fontSize = if (face) 22.sp else 13.sp,
                face = face,
                icon = icon,
                haptic = style.haptics,
                onPress = { callbacks.onButton(bit, true) },
                onRelease = { callbacks.onButton(bit, false) }
            )
        }

        @Composable
        fun Face(cx: Float, cy: Float, label: FaceLabel) =
            Key(at(cx, cy, h * NanoLayout.FACE), label.bit, label.text, face = true, labelColor = label.color, icon = label.icon)

        @Composable
        fun Trigger(cx: Float, left: Boolean, label: String) {
            TriggerBar(
                modifier = at(cx, NanoLayout.SHOULDER_Y, w * NanoLayout.TRIGGER_W, h * NanoLayout.SHOULDER_H),
                label = label,
                deepensLeftward = !left,
                hair = style.hairTriggers,
                haptic = style.haptics,
                onValueChange = { callbacks.onTrigger(left, it) }
            )
        }

        val shoulder = RoundedCornerShape(50)
        val triggerX = if (style.swapShoulders) NanoLayout.SWAPPED_TRIGGER_X else NanoLayout.INNER_X
        val bumperX = if (style.swapShoulders) NanoLayout.SWAPPED_BUMPER_X else NanoLayout.OUTER_X
        Trigger(triggerX, left = true, label = labels.leftTrigger)
        Key(at(bumperX, NanoLayout.SHOULDER_Y, w * NanoLayout.SHOULDER_W, h * NanoLayout.SHOULDER_H),
            GamepadReport.BUTTON_LB, label = labels.leftBumper, shape = shoulder)
        Key(at(1f - bumperX, NanoLayout.SHOULDER_Y, w * NanoLayout.SHOULDER_W, h * NanoLayout.SHOULDER_H),
            GamepadReport.BUTTON_RB, label = labels.rightBumper, shape = shoulder)
        Trigger(1f - triggerX, left = false, label = labels.rightTrigger)

        val (stickX, stickY) = if (style.symmetric) NanoLayout.DPAD_X to NanoLayout.DPAD_Y else NanoLayout.LEFT_STICK_X to NanoLayout.LEFT_STICK_Y
        val (dpadX, dpadY) = if (style.symmetric) NanoLayout.LEFT_STICK_X to NanoLayout.LEFT_STICK_Y else NanoLayout.DPAD_X to NanoLayout.DPAD_Y
        VirtualStick(
            modifier = at(stickX, stickY, h * NanoLayout.STICK),
            onMove = { callbacks.onStick(true, it) }
        )
        DPad(
            modifier = at(dpadX, dpadY, h * NanoLayout.DPAD),
            haptic = style.haptics,
            onHatChange = callbacks.onHat
        )
        VirtualStick(
            modifier = at(NanoLayout.RIGHT_STICK_X, NanoLayout.RIGHT_STICK_Y, h * NanoLayout.STICK),
            onMove = { callbacks.onStick(false, it) }
        )

        val guideY = if (style.share) NanoLayout.SERIES_GUIDE_Y else NanoLayout.GUIDE_Y
        val viewMenuY = if (style.share) NanoLayout.SERIES_VIEW_MENU_Y else NanoLayout.VIEW_MENU_Y
        Key(at(0.5f, guideY, h * NanoLayout.GUIDE), GamepadReport.BUTTON_GUIDE, icon = labels.guideIcon)
        Key(at(NanoLayout.VIEW_X, viewMenuY, h * NanoLayout.VIEW_MENU), GamepadReport.BUTTON_VIEW, icon = labels.viewIcon)
        Key(at(NanoLayout.MENU_X, viewMenuY, h * NanoLayout.VIEW_MENU), GamepadReport.BUTTON_MENU, icon = labels.menuIcon)
        if (style.share) {
            Key(at(0.5f, NanoLayout.SERIES_SHARE_Y, h * NanoLayout.VIEW_MENU), GamepadReport.BUTTON_SHARE, icon = { shareIcon(it) })
        }

        val dx = NanoLayout.FACE_DX
        val dy = NanoLayout.FACE_DY
        Face(NanoLayout.FACE_X, NanoLayout.FACE_Y - dy, labels.north)
        Face(NanoLayout.FACE_X - dx, NanoLayout.FACE_Y, labels.west)
        Face(NanoLayout.FACE_X + dx, NanoLayout.FACE_Y, labels.east)
        Face(NanoLayout.FACE_X, NanoLayout.FACE_Y + dy, labels.south)

        Key(at(NanoLayout.LS_X, NanoLayout.STICK_CLICK_Y, h * NanoLayout.STICK_CLICK), GamepadReport.BUTTON_LS, label = labels.leftStick)
        Key(at(NanoLayout.RS_X, NanoLayout.STICK_CLICK_Y, h * NanoLayout.STICK_CLICK), GamepadReport.BUTTON_RS, label = labels.rightStick)
    }
}
