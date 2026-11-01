package app.sucareto.hidora.core.gadget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.os.RemoteException
import com.topjohnwu.superuser.ipc.RootService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class GadgetResult(
    val success: Boolean,
    val error: String? = null,
    val activeGadget: String? = null,
    val isBound: Boolean = false,
    val hidHandles: Map<String, Long> = emptyMap(),
    val ffsHandles: Map<String, LongArray> = emptyMap(),
    val systemUdcRestored: Boolean = true,
    val io: EndpointIo? = null
)

class GadgetClient(private val context: Context, private val onDisconnected: () -> Unit) {
    private class ConnectionReset : Exception()

    @Volatile private var connection = CompletableDeferred<IGadgetService>()
    @Volatile private var bound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            connection.complete(IGadgetService.Stub.asInterface(binder))
        }

        override fun onServiceDisconnected(name: ComponentName) {
            reset()
            onDisconnected()
        }
    }

    fun bind() {
        if (bound) return
        bound = true
        RootService.bind(Intent(context, GadgetService::class.java), serviceConnection)
    }

    fun unbind() {
        RootService.unbind(serviceConnection)
        reset()
    }

    private fun reset() {
        bound = false
        val previous = connection
        connection = CompletableDeferred()
        previous.completeExceptionally(ConnectionReset())
    }

    suspend fun rebuild(spec: GadgetSpec): GadgetResult = call { it.rebuild(spec.toBundle()) }

    suspend fun activate(gadgetName: String): GadgetResult = call { it.activate(gadgetName) }

    suspend fun deactivate(gadgetName: String): GadgetResult = call { it.deactivate(gadgetName) }

    suspend fun delete(gadgetName: String): GadgetResult = call { it.remove(gadgetName) }

    suspend fun queryState(): GadgetResult = call { it.queryState() }

    suspend fun openHid(gadgetName: String): GadgetResult = call { it.openHid(gadgetName) }

    suspend fun prepareFfs(spec: GadgetSpec): GadgetResult = call { it.prepareFfs(spec.toBundle()) }

    suspend fun checkEnvironment(): List<EnvCheck>? {
        val reply = remote { it.checkEnvironment() }.getOrNull()?.first ?: return null
        return if (reply.getBoolean(GadgetReply.KEY_SUCCESS)) EnvChecks.fromBundle(reply) else null
    }

    private suspend fun service(): IGadgetService? = withTimeoutOrNull(CONNECT_TIMEOUT_MS) {
        var service: IGadgetService? = null
        while (service == null) {
            bind()
            service = try {
                connection.await()
            } catch (e: ConnectionReset) {
                null
            }
        }
        service
    } ?: null.also { reset() }

    private suspend fun remote(block: (IGadgetService) -> Bundle): Result<Pair<Bundle, IGadgetService>> {
        val service = service() ?: return Result.failure(IllegalStateException("service not connected"))
        return withContext(Dispatchers.IO) {
            try {
                Result.success(block(service) to service)
            } catch (e: RemoteException) {
                Result.failure(e)
            }
        }
    }

    private suspend fun call(block: (IGadgetService) -> Bundle): GadgetResult =
        remote(block).fold(
            onSuccess = { (reply, service) -> parse(reply, RemoteEndpointIo(service)) },
            onFailure = { GadgetResult(success = false, error = it.message ?: it.javaClass.simpleName) }
        )

    private fun parse(b: Bundle, io: EndpointIo): GadgetResult {
        val hid = b.getBundle(GadgetReply.KEY_HID_HANDLES)
        val ffs = b.getBundle(GadgetReply.KEY_FFS_HANDLES)
        return GadgetResult(
            success = b.getBoolean(GadgetReply.KEY_SUCCESS),
            error = b.getString(GadgetReply.KEY_ERROR),
            activeGadget = b.getString(GadgetReply.KEY_ACTIVE_GADGET)?.takeIf { it.isNotEmpty() },
            isBound = b.getBoolean(GadgetReply.KEY_IS_BOUND),
            hidHandles = hid?.keySet()?.associateWith { hid.getLong(it) }.orEmpty(),
            ffsHandles = ffs?.keySet()?.associateWith { ffs.getLongArray(it) ?: LongArray(0) }.orEmpty(),
            systemUdcRestored = b.getBoolean(GadgetReply.KEY_SYSTEM_UDC_RESTORED, true),
            io = io
        )
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 5000L
    }
}
