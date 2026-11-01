package app.sucareto.hidora.profile.keyboardmouse

import androidx.annotation.StringRes
import app.sucareto.hidora.R

enum class MouseSpeed(val gain: Float, @StringRes val labelRes: Int) {
    LOW(1.0f, R.string.mouse_speed_low),
    MEDIUM(2.0f, R.string.mouse_speed_mid),
    HIGH(3.2f, R.string.mouse_speed_high);

    fun next(): MouseSpeed = entries[(ordinal + 1) % entries.size]

    companion object {
        val DEFAULT = MEDIUM
    }
}
