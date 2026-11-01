package app.sucareto.hidora.profile.xinput

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlin.math.max

class Rumble(context: Context, private val enabled: () -> Boolean) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }
    private val amplitudeControl = vibrator?.hasAmplitudeControl() == true
    private var current = 0

    @Synchronized
    fun set(strong: Int, weak: Int) {
        val level = if (enabled()) max(strong, weak) else 0
        val target = when {
            level == 0 -> 0
            amplitudeControl -> ((level + STEP - 1) / STEP * STEP).coerceIn(1, 255)
            else -> VibrationEffect.DEFAULT_AMPLITUDE
        }
        if (target == current) return
        current = target
        val v = vibrator ?: return
        if (target == 0) {
            v.cancel()
        } else {
            v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, SEGMENT_MS), intArrayOf(0, target), 1))
        }
    }

    fun stop() = set(0, 0)

    private companion object {
        const val STEP = 16
        const val SEGMENT_MS = 1000L
    }
}
