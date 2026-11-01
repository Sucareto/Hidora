package app.sucareto.hidora.profile.gamepad.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import app.sucareto.hidora.profile.gamepad.GamepadReport
import app.sucareto.hidora.profile.gamepad.LabelStyle

class FaceLabel(val bit: Int, val text: String? = null, val icon: (DrawScope.(Color) -> Unit)? = null, val color: Color)

class GamepadLabels(
    val north: FaceLabel,
    val east: FaceLabel,
    val south: FaceLabel,
    val west: FaceLabel,
    val leftBumper: String,
    val leftTrigger: String,
    val rightBumper: String,
    val rightTrigger: String,
    val leftStick: String,
    val rightStick: String,
    val viewIcon: DrawScope.(Color) -> Unit,
    val menuIcon: DrawScope.(Color) -> Unit,
    val guideIcon: DrawScope.(Color) -> Unit
)

private val NintendoLetter = Color(0xFFE6E6E6)

fun labelsFor(style: LabelStyle): GamepadLabels = when (style) {
    LabelStyle.XBOX -> GamepadLabels(
        north = FaceLabel(GamepadReport.BUTTON_Y, "Y", color = GamepadColors.LetterY),
        east = FaceLabel(GamepadReport.BUTTON_B, "B", color = GamepadColors.LetterB),
        south = FaceLabel(GamepadReport.BUTTON_A, "A", color = GamepadColors.LetterA),
        west = FaceLabel(GamepadReport.BUTTON_X, "X", color = GamepadColors.LetterX),
        leftBumper = "LB", leftTrigger = "LT", rightBumper = "RB", rightTrigger = "RT",
        leftStick = "LS", rightStick = "RS",
        viewIcon = { viewIcon(it) }, menuIcon = { menuIcon(it) }, guideIcon = { guideIcon(it) }
    )
    LabelStyle.PS -> GamepadLabels(
        north = FaceLabel(GamepadReport.BUTTON_Y, icon = { triangleIcon(it) }, color = Color(0xFF3FBFA0)),
        east = FaceLabel(GamepadReport.BUTTON_B, icon = { circleIcon(it) }, color = Color(0xFFE8504F)),
        south = FaceLabel(GamepadReport.BUTTON_A, icon = { crossIcon(it) }, color = Color(0xFF7C9CF0)),
        west = FaceLabel(GamepadReport.BUTTON_X, icon = { squareIcon(it) }, color = Color(0xFFD884C8)),
        leftBumper = "L1", leftTrigger = "L2", rightBumper = "R1", rightTrigger = "R2",
        leftStick = "L3", rightStick = "R3",
        viewIcon = { createIcon(it) }, menuIcon = { optionsIcon(it) }, guideIcon = { guideIcon(it) }
    )
    LabelStyle.NS -> GamepadLabels(
        north = FaceLabel(GamepadReport.BUTTON_X, "X", color = NintendoLetter),
        east = FaceLabel(GamepadReport.BUTTON_A, "A", color = NintendoLetter),
        south = FaceLabel(GamepadReport.BUTTON_B, "B", color = NintendoLetter),
        west = FaceLabel(GamepadReport.BUTTON_Y, "Y", color = NintendoLetter),
        leftBumper = "L", leftTrigger = "ZL", rightBumper = "R", rightTrigger = "ZR",
        leftStick = "LS", rightStick = "RS",
        viewIcon = { minusIcon(it) }, menuIcon = { plusIcon(it) }, guideIcon = { homeIcon(it) }
    )
}

fun DrawScope.shareIcon(color: Color) {
    val width = size.minDimension * 0.09f
    val r = size.minDimension * 0.24f
    drawLine(color, Offset(center.x, center.y + r * 0.35f), Offset(center.x, center.y - r), width)
    drawLine(color, Offset(center.x - r * 0.5f, center.y - r * 0.5f), Offset(center.x, center.y - r), width)
    drawLine(color, Offset(center.x + r * 0.5f, center.y - r * 0.5f), Offset(center.x, center.y - r), width)
    drawLine(color, Offset(center.x - r, center.y + r), Offset(center.x + r, center.y + r), width)
}

private fun DrawScope.symbolStroke() = Stroke(size.minDimension * 0.08f)

private fun DrawScope.triangleIcon(color: Color) {
    val r = size.minDimension * 0.26f
    val path = Path().apply {
        moveTo(center.x, center.y - r)
        lineTo(center.x + r * 0.95f, center.y + r * 0.62f)
        lineTo(center.x - r * 0.95f, center.y + r * 0.62f)
        close()
    }
    drawPath(path, color, style = symbolStroke())
}

private fun DrawScope.circleIcon(color: Color) {
    drawCircle(color, size.minDimension * 0.24f, style = symbolStroke())
}

private fun DrawScope.crossIcon(color: Color) {
    val r = size.minDimension * 0.22f
    val width = size.minDimension * 0.08f
    drawLine(color, Offset(center.x - r, center.y - r), Offset(center.x + r, center.y + r), width)
    drawLine(color, Offset(center.x - r, center.y + r), Offset(center.x + r, center.y - r), width)
}

private fun DrawScope.squareIcon(color: Color) {
    val r = size.minDimension * 0.21f
    drawRect(color, Offset(center.x - r, center.y - r), Size(r * 2, r * 2), style = symbolStroke())
}

private fun DrawScope.minusIcon(color: Color) {
    val r = size.minDimension * 0.2f
    drawLine(color, Offset(center.x - r, center.y), Offset(center.x + r, center.y), size.minDimension * 0.08f)
}

private fun DrawScope.plusIcon(color: Color) {
    val r = size.minDimension * 0.2f
    val width = size.minDimension * 0.08f
    drawLine(color, Offset(center.x - r, center.y), Offset(center.x + r, center.y), width)
    drawLine(color, Offset(center.x, center.y - r), Offset(center.x, center.y + r), width)
}

private fun DrawScope.createIcon(color: Color) {
    val width = size.minDimension * 0.07f
    val r = size.minDimension * 0.2f
    drawLine(color, Offset(center.x, center.y - r), Offset(center.x, center.y + r * 0.2f), width)
    drawLine(color, Offset(center.x - r * 0.75f, center.y - r * 0.55f), Offset(center.x - r * 0.25f, center.y + r * 0.35f), width)
    drawLine(color, Offset(center.x + r * 0.75f, center.y - r * 0.55f), Offset(center.x + r * 0.25f, center.y + r * 0.35f), width)
}

private fun DrawScope.optionsIcon(color: Color) {
    val width = size.minDimension * 0.06f
    val half = size.minDimension * 0.17f
    for (i in -1..1) {
        val y = center.y + i * size.minDimension * 0.11f
        drawLine(color, Offset(center.x - half, y), Offset(center.x + half, y), width)
    }
}

private fun DrawScope.homeIcon(color: Color) {
    val r = size.minDimension * 0.22f
    val path = Path().apply {
        moveTo(center.x, center.y - r)
        lineTo(center.x + r, center.y - r * 0.05f)
        lineTo(center.x + r * 0.7f, center.y - r * 0.05f)
        lineTo(center.x + r * 0.7f, center.y + r * 0.8f)
        lineTo(center.x - r * 0.7f, center.y + r * 0.8f)
        lineTo(center.x - r * 0.7f, center.y - r * 0.05f)
        lineTo(center.x - r, center.y - r * 0.05f)
        close()
    }
    drawPath(path, color)
}
