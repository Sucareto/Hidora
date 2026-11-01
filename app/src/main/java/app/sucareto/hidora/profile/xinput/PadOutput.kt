package app.sucareto.hidora.profile.xinput

import androidx.compose.ui.geometry.Offset

interface PadOutput {
    fun setButton(bit: Int, pressed: Boolean)
    fun setHat(hat: Int)
    fun setTrigger(left: Boolean, value: Int)
    fun setStick(left: Boolean, position: Offset)
    fun releaseAll()
    fun close()
}

object PadIo {
    const val QUEUE_CAPACITY = 64
    const val IDLE_POLL_MS = 1000L
    const val RETRY_DELAY_MS = 50L
    const val NOT_ENABLED_RETRY_MS = 250L
}
