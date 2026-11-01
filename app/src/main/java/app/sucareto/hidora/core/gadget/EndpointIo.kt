package app.sucareto.hidora.core.gadget

import android.os.RemoteException
import android.system.OsConstants

interface EndpointIo {
    val isAlive: Boolean
    fun write(handle: Long, data: ByteArray): Int
    fun read(handle: Long, buffer: ByteArray): Int
    fun poll(handle: Long, timeoutMs: Int): Int
    fun close(handle: Long)
}

internal class RemoteEndpointIo(private val service: IGadgetService) : EndpointIo {
    override val isAlive: Boolean get() = service.asBinder().isBinderAlive

    override fun write(handle: Long, data: ByteArray): Int = remote { service.write(handle, data) }

    override fun read(handle: Long, buffer: ByteArray): Int = remote { service.read(handle, buffer) }

    override fun poll(handle: Long, timeoutMs: Int): Int = remote { service.poll(handle, timeoutMs) }

    override fun close(handle: Long) {
        remote { service.close(handle); 0 }
    }

    private inline fun remote(block: () -> Int): Int = try {
        block()
    } catch (e: RemoteException) {
        -OsConstants.ENOTCONN
    }
}
