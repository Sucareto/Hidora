package app.sucareto.hidora.profile.keyboardmouse.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import app.sucareto.hidora.profile.keyboardmouse.MouseReporter
import app.sucareto.hidora.ui.theme.Dimens
import app.sucareto.hidora.ui.theme.LocalAppExtraColors
import kotlin.math.abs

@Composable
fun Trackpad(
    modifier: Modifier = Modifier,
    baseGain: Float,
    onMove: (dx: Int, dy: Int) -> Unit,
    onWheel: (delta: Int) -> Unit
) {
    val moveRemainder = remember { floatArrayOf(0f, 0f) }
    val currentGain by rememberUpdatedState(baseGain)
    val move by rememberUpdatedState(onMove)
    val wheel by rememberUpdatedState(onWheel)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(LocalAppExtraColors.current.trackpadSurface)
            .border(Dimens.TrackpadBorderWidth, LocalAppExtraColors.current.trackpadBorder)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(pass = PointerEventPass.Main)
                    var lastPos = down.position
                    var isTwoFinger = false

                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break
                        if (pressed.size >= 2) isTwoFinger = true
                        val primary = pressed.firstOrNull { it.id == down.id } ?: pressed.first()
                        val dxPx = primary.position.x - lastPos.x
                        val dyPx = primary.position.y - lastPos.y
                        if (abs(dxPx) > 0.5f || abs(dyPx) > 0.5f) {
                            if (isTwoFinger) {
                                if (abs(dyPx) > 1f) wheel(if (dyPx > 0) -1 else 1)
                            } else {
                                val dxDp = dxPx.toDp().value
                                val dyDp = dyPx.toDp().value
                                val gdx = dxDp * currentGain + moveRemainder[0]
                                val gdy = dyDp * currentGain + moveRemainder[1]
                                var ix = gdx.toInt()
                                var iy = gdy.toInt()
                                moveRemainder[0] = gdx - ix
                                moveRemainder[1] = gdy - iy
                                while (ix != 0 || iy != 0) {
                                    val stepX = ix.coerceIn(-MouseReporter.MAX_DELTA, MouseReporter.MAX_DELTA)
                                    val stepY = iy.coerceIn(-MouseReporter.MAX_DELTA, MouseReporter.MAX_DELTA)
                                    move(stepX, stepY)
                                    ix -= stepX
                                    iy -= stepY
                                }
                            }
                        }
                        lastPos = primary.position
                        event.changes.forEach { it.consume() }
                    }
                }
            }
    )
}
