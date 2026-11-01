package app.sucareto.hidora.core.hid

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ResendLoop(private val scope: CoroutineScope, private val resend: () -> Result<Unit>) {
    private val pending = AtomicBoolean(false)

    fun request() {
        if (!pending.compareAndSet(false, true)) return
        scope.launch {
            try {
                repeat(MAX_ATTEMPTS) {
                    delay(RETRY_DELAY_MS)
                    if (resend().isSuccess) return@launch
                }
            } finally {
                pending.set(false)
            }
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 50
        const val RETRY_DELAY_MS = 20L
    }
}
