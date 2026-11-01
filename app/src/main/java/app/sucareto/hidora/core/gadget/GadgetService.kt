package app.sucareto.hidora.core.gadget

import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import app.sucareto.hidora.util.AppLog
import com.topjohnwu.superuser.ipc.RootService

class GadgetService : RootService() {
    private val controlLock = Any()

    private val binder = object : IGadgetService.Stub() {
        override fun rebuild(spec: Bundle): Bundle = control("rebuild") { reply ->
            val gadget = specFrom(spec)
            val state = GadgetOperations.queryGlobalState()
            var restored = true
            if (state.activeGadget != null && state.isBound) {
                GadgetOperations.deactivate(state.activeGadget)
                restored = GadgetOperations.restoreSystemUdc()
            }
            GadgetOperations.create(gadget)
            reply.putBoolean(GadgetReply.KEY_SYSTEM_UDC_RESTORED, restored)
        }

        override fun activate(name: String): Bundle = control("activate") { reply ->
            putHandles(reply, GadgetOperations.activate(checkedName(name)))
        }

        override fun deactivate(name: String): Bundle = control("deactivate") { reply ->
            GadgetOperations.deactivate(checkedName(name))
            reply.putBoolean(GadgetReply.KEY_SYSTEM_UDC_RESTORED, GadgetOperations.restoreSystemUdc())
        }

        override fun remove(name: String): Bundle = control("remove") {
            val gadget = checkedName(name)
            GadgetOperations.deactivate(gadget)
            GadgetOperations.remove(gadget)
            GadgetOperations.restoreSystemUdc()
        }

        override fun queryState(): Bundle = control("queryState") { reply ->
            val state = GadgetOperations.queryGlobalState()
            reply.putString(GadgetReply.KEY_ACTIVE_GADGET, state.activeGadget.orEmpty())
            reply.putBoolean(GadgetReply.KEY_IS_BOUND, state.isBound)
        }

        override fun openHid(name: String): Bundle = control("openHid") { reply ->
            putHandles(reply, GadgetOperations.openHid(checkedName(name)))
        }

        override fun prepareFfs(spec: Bundle): Bundle = control("prepareFfs") { reply ->
            val handles = GadgetOperations.prepareFfs(specFrom(spec))
            reply.putBundle(GadgetReply.KEY_FFS_HANDLES, Bundle().apply { handles.forEach { (fn, h) -> putLongArray(fn, h) } })
        }

        override fun checkEnvironment(): Bundle = control("checkEnvironment") { reply ->
            EnvChecks.toBundle(RootEnvironment.run(), reply)
        }

        override fun write(handle: Long, data: ByteArray): Int = EndpointTable.write(handle, data)

        override fun read(handle: Long, buffer: ByteArray): Int = EndpointTable.read(handle, buffer)

        override fun poll(handle: Long, timeoutMs: Int): Int = EndpointTable.poll(handle, timeoutMs)

        override fun close(handle: Long) = EndpointTable.close(handle)
    }

    override fun onBind(intent: Intent): IBinder = binder

    private fun control(name: String, block: (Bundle) -> Unit): Bundle = synchronized(controlLock) {
        val reply = Bundle()
        try {
            block(reply)
            reply.putBoolean(GadgetReply.KEY_SUCCESS, true)
        } catch (e: Exception) {
            AppLog.e(TAG, "$name failed", e)
            reply.putBoolean(GadgetReply.KEY_SUCCESS, false)
            reply.putString(GadgetReply.KEY_ERROR, e.message)
        }
        AppLog.i(TAG) { "$name success=${reply.getBoolean(GadgetReply.KEY_SUCCESS)}" }
        reply
    }

    private fun putHandles(reply: Bundle, handles: Map<String, Long>) {
        reply.putBundle(GadgetReply.KEY_HID_HANDLES, Bundle().apply { handles.forEach { (fn, h) -> putLong(fn, h) } })
    }

    private fun specFrom(b: Bundle): GadgetSpec = GadgetSpec.fromBundle(b).also { it.validate() }

    private fun checkedName(name: String): String = name.also {
        require(GadgetSpec.isValidName(it)) { "invalid gadget name: $it" }
    }

    private companion object {
        const val TAG = "GadgetService"
    }
}
