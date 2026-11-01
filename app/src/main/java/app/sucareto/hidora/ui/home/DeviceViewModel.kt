package app.sucareto.hidora.ui.home

import android.app.Application
import android.os.Build
import android.system.Os
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.sucareto.hidora.R
import app.sucareto.hidora.core.ffs.FfsFunction
import app.sucareto.hidora.core.ffs.FfsFunctions
import app.sucareto.hidora.core.gadget.CheckStatus
import app.sucareto.hidora.core.gadget.EndpointIo
import app.sucareto.hidora.core.gadget.EnvCheck
import app.sucareto.hidora.core.gadget.EnvChecks
import app.sucareto.hidora.core.gadget.GadgetClient
import app.sucareto.hidora.core.gadget.GadgetReply
import app.sucareto.hidora.core.gadget.GadgetResult
import app.sucareto.hidora.core.gadget.GadgetSpec
import app.sucareto.hidora.core.hid.HidEndpoints
import app.sucareto.hidora.core.profile.AppSettings
import app.sucareto.hidora.core.profile.DeviceIo
import app.sucareto.hidora.core.profile.GadgetProfile
import app.sucareto.hidora.core.profile.ProfileSession
import app.sucareto.hidora.profile.ProfileRegistry
import app.sucareto.hidora.profile.xinput.XusbSupport
import app.sucareto.hidora.util.AppLog
import com.topjohnwu.superuser.Shell
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val TAG = "DeviceViewModel"

class ActiveDevice(
    val profile: GadgetProfile,
    val spec: GadgetSpec,
    val session: ProfileSession,
    internal val io: DeviceIo,
    internal val remote: EndpointIo? = null,
    val preview: Boolean = false
)

enum class RootAccess { UNKNOWN, GRANTED, DENIED }

private val GadgetSpec.usesFfs: Boolean get() = ffsFunctions.isNotEmpty()

class DeviceViewModel(application: Application) : AndroidViewModel(application) {
    private val profiles = ProfileRegistry.profiles
    private val gadgetClient = GadgetClient(application, ::onRootDisconnected)
    private val closer = Executors.newSingleThreadExecutor()
    private val rootMutex = Mutex()

    var rootAccessState by mutableStateOf(RootAccess.UNKNOWN)
        private set
    private val _statusEvents = Channel<DeviceStatus>(Channel.BUFFERED)
    internal val statusEvents = _statusEvents.receiveAsFlow()
    private var isBusyState by mutableStateOf(false)
    var selectedProfileIdState by mutableStateOf(profiles.first().id)
        private set
    private var existingGadgetState by mutableStateOf<String?>(null)
    private var isBoundState by mutableStateOf(false)

    var activeDevice by mutableStateOf<ActiveDevice?>(null)
        private set

    init {
        if (AppSettings.get(application).rootGranted) {
            runExclusive {
                if (ensureRoot()) existingGadgetState?.let { ProfileRegistry.byGadgetName(it) }?.let { selectedProfileIdState = it.id }
            }
        }
    }

    private suspend fun ensureRoot(): Boolean = rootMutex.withLock {
        if (rootAccessState == RootAccess.GRANTED) return@withLock true
        val granted = withContext(Dispatchers.IO) {
            Shell.getCachedShell()?.takeIf { !it.isRoot }?.let { runCatching { it.close() } }
            Shell.getShell().isRoot
        }
        AppLog.i(TAG) { "root granted=$granted" }
        AppSettings.get(getApplication()).rootGranted = granted
        rootAccessState = if (granted) RootAccess.GRANTED else RootAccess.DENIED
        if (granted) {
            gadgetClient.bind()
            refreshGlobalState()
        }
        granted
    }

    private fun onRootDisconnected() {
        AppLog.w(TAG, "root service disconnected")
        if (activeDevice?.preview != true) closeActiveDevice()
        existingGadgetState = null
        isBoundState = false
        runExclusive { refreshGlobalState() }
    }

    fun onSelect(profileId: String) {
        selectedProfileIdState = profileId
    }

    fun releaseInputs() {
        activeDevice?.session?.releaseInputs()
    }

    suspend fun checkEnvironment(): List<EnvCheck> {
        val app = getApplication<Application>()
        val device = EnvCheck(EnvChecks.DEVICE, CheckStatus.INFO,
            "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        val granted = ensureRoot()
        val remote = if (granted) gadgetClient.checkEnvironment() else null
        val root = EnvCheck(EnvChecks.ROOT_ACCESS, when {
            !granted -> CheckStatus.FAIL
            remote == null -> CheckStatus.WARN
            else -> CheckStatus.OK
        }, "")
        val kernel = EnvCheck(EnvChecks.KERNEL, if (XusbSupport.isSupported(app)) CheckStatus.OK else CheckStatus.INFO, Os.uname().release)
        return listOf(device, root) + remote.orEmpty() + kernel
    }

    fun isDevicePresent(profile: GadgetProfile): Boolean = existingGadgetState == profile.gadgetName

    fun onLeaveDevice() {
        releaseInputs()
        if (activeDevice?.preview == true) {
            closeActiveDevice()
            return
        }
        if (!AppSettings.get(getApplication()).stopOnLeave) return
        val profile = activeDevice?.profile ?: return
        runExclusive {
            stop(profile)
            refreshGlobalState()
        }
    }

    override fun onCleared() {
        closeActiveDevice()
        closer.shutdown()
        gadgetClient.unbind()
    }

    private suspend fun refreshGlobalState() {
        val result = gadgetClient.queryState()
        AppLog.i(TAG) { "refreshGlobalState active=${result.activeGadget} bound=${result.isBound} success=${result.success}" }
        if (!result.success) return
        existingGadgetState = result.activeGadget
        isBoundState = result.isBound
        val profile = result.activeGadget?.let { ProfileRegistry.byGadgetName(it) }
        if (profile != null && result.isBound) {
            if (activeDevice?.profile == profile && activeDevice?.remote?.isAlive != false) return
            val spec = profile.spec(getApplication())
            if (spec.usesFfs) {
                closeActiveDevice()
                gadgetClient.deactivate(profile.gadgetName)
                isBoundState = false
            } else {
                openActiveDevice(profile, spec, gadgetClient.openHid(profile.gadgetName))
            }
        } else {
            closeActiveDevice()
            if (profile != null) gadgetClient.deactivate(profile.gadgetName)
        }
    }

    private fun openActiveDevice(profile: GadgetProfile, spec: GadgetSpec, opened: GadgetResult): Boolean {
        closeActiveDevice()
        val functionNames = spec.functions.map { it.name }
        val io = opened.io
        if (!opened.success || io == null || !opened.hidHandles.keys.containsAll(functionNames)) {
            AppLog.w(TAG, "cannot open ${profile.id}: ${opened.error}, have ${opened.hidHandles.keys}")
            opened.io?.let { remote -> opened.hidHandles.values.forEach(remote::close) }
            return false
        }
        val endpoints = HidEndpoints.remote(io, opened.hidHandles.filterKeys { it in functionNames })
        val device = DeviceIo(endpoints, FfsFunctions.EMPTY)
        activeDevice = ActiveDevice(profile, spec, profile.createSession(getApplication(), spec, device), device, io)
        return true
    }

    private fun ffsFunctions(spec: GadgetSpec, prepared: GadgetResult): FfsFunctions? {
        val io = prepared.io ?: return null
        val handles = spec.ffsFunctions.map { fn -> fn.name to (prepared.ffsHandles[fn.name] ?: return null) }
        return FfsFunctions(handles.associate { (name, h) -> name to FfsFunction.remote(name, io, h) })
    }

    private suspend fun startFresh(profile: GadgetProfile, spec: GadgetSpec): Boolean {
        releaseActiveDevice()
        val built = gadgetClient.rebuild(spec)
        if (!built.success) {
            report(GadgetAction.START, built)
            return false
        }
        val started = if (spec.usesFfs) activateFfs(profile, spec) else activateHid(profile, spec)
        if (!started) gadgetClient.delete(profile.gadgetName)
        return started
    }

    private suspend fun activateHid(profile: GadgetProfile, spec: GadgetSpec): Boolean {
        val result = gadgetClient.activate(profile.gadgetName)
        if (!result.success) {
            report(GadgetAction.START, result)
            return false
        }
        val opened = openActiveDevice(profile, spec, result)
        if (!opened) _statusEvents.trySend(DeviceStatus.OpenError)
        return opened
    }

    private suspend fun activateFfs(profile: GadgetProfile, spec: GadgetSpec): Boolean {
        val prepared = gadgetClient.prepareFfs(spec)
        if (prepared.error == GadgetReply.ERROR_CODE_DESCRIPTORS_REJECTED) {
            profile.onDescriptorsRejected(getApplication(), spec)
            _statusEvents.trySend(DeviceStatus.DescriptorRejected)
            return false
        }
        if (!prepared.success) {
            report(GadgetAction.START, prepared)
            return false
        }
        val ffs = ffsFunctions(spec, prepared) ?: run {
            _statusEvents.trySend(DeviceStatus.OpenError)
            return false
        }
        val io = DeviceIo(HidEndpoints.EMPTY, ffs)
        val session = profile.createSession(getApplication(), spec, io)
        val result = gadgetClient.activate(profile.gadgetName)
        if (!result.success) {
            session.close()
            withContext(Dispatchers.IO) { io.closeAll() }
            report(GadgetAction.START, result)
            return false
        }
        activeDevice = ActiveDevice(profile, spec, session, io, prepared.io)
        return true
    }

    private suspend fun releaseActiveDevice(): GadgetResult? {
        val device = activeDevice ?: return null
        if (device.preview || !device.spec.usesFfs) {
            closeActiveDevice()
            return null
        }
        activeDevice = null
        device.session.close()
        val result = gadgetClient.deactivate(device.profile.gadgetName)
        withContext(Dispatchers.IO) { device.io.closeAll() }
        return result
    }

    private fun closeActiveDevice() {
        val device = activeDevice ?: return
        activeDevice = null
        device.session.close()
        closer.execute { device.io.closeAll() }
    }

    private suspend fun stop(profile: GadgetProfile) {
        val deactivated = releaseActiveDevice() ?: gadgetClient.deactivate(profile.gadgetName)
        val deleted = gadgetClient.delete(profile.gadgetName)
        report(GadgetAction.STOP, if (!deactivated.success || deleted.success) deactivated else deleted)
    }

    fun deviceRows(): List<DeviceRowUiState> {
        val app = getApplication<Application>()
        return profiles.map { profile ->
            val exists = existingGadgetState == profile.gadgetName
            val running = exists && isBoundState
            val stateLabel = app.getString(
                when {
                    running -> R.string.state_running
                    exists -> R.string.state_stopped_leftover
                    else -> R.string.state_idle
                }
            )
            DeviceRowUiState(profile.id, app.getString(profile.titleRes), stateLabel, running, profile.hasSettings)
        }
    }

    fun selectedActions(onEntered: () -> Unit): DeviceActionsUiState {
        val profile = ProfileRegistry.byId(selectedProfileIdState) ?: profiles.first()
        return DeviceActionsUiState(
            stopEnabled = existingGadgetState == profile.gadgetName,
            isBusy = isBusyState,
            onEnter = { onEnter(profile, onEntered) },
            onStop = { onStop(profile) }
        )
    }

    private fun runExclusive(block: suspend () -> Unit) {
        if (isBusyState) return
        isBusyState = true
        viewModelScope.launch {
            try {
                block()
            } finally {
                isBusyState = false
            }
        }
    }

    private fun enterPreview(profile: GadgetProfile, onEntered: () -> Unit) {
        closeActiveDevice()
        val app = getApplication<Application>()
        activeDevice = ActiveDevice(
            profile,
            profile.spec(app),
            profile.createPreviewSession(app),
            DeviceIo(HidEndpoints.EMPTY, FfsFunctions.EMPTY),
            preview = true
        )
        _statusEvents.trySend(DeviceStatus.PreviewMode)
        onEntered()
    }

    private fun onEnter(profile: GadgetProfile, onEntered: () -> Unit) = runExclusive {
        if (ensureRoot()) enterDevice(profile, onEntered) else enterPreview(profile, onEntered)
    }

    private suspend fun enterDevice(profile: GadgetProfile, onEntered: () -> Unit) {
        val current = activeDevice
        if (current == null || current.profile != profile || current.preview) {
            val spec = profile.spec(getApplication())
            val state = gadgetClient.queryState()
            val reusable = state.success && state.activeGadget == profile.gadgetName && state.isBound && !spec.usesFfs
            val started = if (reusable) {
                openActiveDevice(profile, spec, gadgetClient.openHid(profile.gadgetName)).also {
                    if (!it) _statusEvents.trySend(DeviceStatus.OpenError)
                }
            } else {
                startFresh(profile, spec)
            }
            refreshGlobalState()
            if (!started) return
        }
        if (activeDevice?.profile == profile) onEntered()
    }

    private fun onStop(profile: GadgetProfile) = runExclusive {
        stop(profile)
        refreshGlobalState()
    }

    private fun report(action: GadgetAction, result: GadgetResult) {
        _statusEvents.trySend(
            if (result.success && !result.systemUdcRestored) DeviceStatus.UdcNotRestored(action)
            else DeviceStatus.ActionResult(action, result.success, result.error)
        )
    }
}
