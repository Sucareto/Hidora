package app.sucareto.hidora.core.gadget

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import app.sucareto.hidora.core.ffs.FfsMounter
import app.sucareto.hidora.util.AppLog
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createSymbolicLinkPointingTo
import kotlin.io.path.deleteIfExists
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isSymbolicLink
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readLines
import kotlin.io.path.readText
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText

class HidResourceExhaustedException(cause: Throwable) :
    Exception(GadgetReply.ERROR_CODE_DEVICE_EXHAUSTED, cause)

class GadgetSlotUnavailableException(cause: Throwable) :
    Exception(GadgetReply.ERROR_CODE_SLOT_UNAVAILABLE, cause)

class FfsDescriptorsRejectedException(cause: Throwable) :
    Exception(GadgetReply.ERROR_CODE_DESCRIPTORS_REJECTED, cause)

object GadgetOperations {
    private const val TAG = "GadgetOperations"
    private val GADGET_ROOT: Path by lazy { configfsRoot() / "usb_gadget" }
    private val UDC_CLASS_ROOT = Path("/sys/class/udc")
    private const val SLOT = "hidora"
    private val SLOT_PATH: Path by lazy { GADGET_ROOT / SLOT }
    private const val DUMMY_UDC_PREFIX = "dummy_udc"
    @Volatile private var systemUdc: String? = null
    private const val CONFIG_NAME = "c.1"
    private const val CONFIG_LABEL = "Config 1"
    private const val STRINGS_EN_US = "strings/0x409"
    private const val OS_DESC_SIGNATURE = "MSFT100"
    private const val FUNCTION_PREFIX = "hid."
    private const val FFS_PREFIX = "ffs."
    private const val NODE_WAIT_TIMEOUT_MS = 3000L
    private const val NODE_WAIT_INTERVAL_MS = 100L
    private const val DIR_MODE = 0b111_101_101
    private const val UNKNOWN_OWNER = "?"

    data class GlobalState(val activeGadget: String?, val isBound: Boolean)

    fun create(spec: GadgetSpec) {
        try {
            build(spec)
        } catch (e: Exception) {
            runCatching { clearSlot() }.onFailure { AppLog.w(TAG, "cleanup after failed create of ${spec.name} failed", it) }
            throw e
        }
    }

    private fun build(spec: GadgetSpec) {
        val gadgetPath = ensureSlot()
        if (isBound(SLOT)) unbindOwn()
        clearSlot()

        (gadgetPath / "idVendor").writeText(hex16(spec.usb.idVendor))
        (gadgetPath / "idProduct").writeText(hex16(spec.usb.idProduct))
        (gadgetPath / "bcdDevice").writeText(hex16(spec.usb.bcdDevice))
        (gadgetPath / "bcdUSB").writeText(hex16(spec.usb.bcdUsb))
        (gadgetPath / "bDeviceClass").writeText(hex8(spec.usb.deviceClass))
        (gadgetPath / "bDeviceSubClass").writeText(hex8(spec.usb.deviceSubClass))
        (gadgetPath / "bDeviceProtocol").writeText(hex8(spec.usb.deviceProtocol))

        val stringsPath = gadgetPath / STRINGS_EN_US
        stringsPath.createDirectories()
        (stringsPath / "manufacturer").writeText(spec.usb.manufacturer)
        (stringsPath / "product").writeText(spec.usb.product)
        if (spec.usb.serialNumber.isNotEmpty()) (stringsPath / "serialnumber").writeText(spec.usb.serialNumber)

        val configPath = gadgetPath / "configs/$CONFIG_NAME"
        val configStringsPath = configPath / STRINGS_EN_US
        configStringsPath.createDirectories()
        (configStringsPath / "configuration").writeText(CONFIG_LABEL)
        (configPath / "MaxPower").writeText(spec.maxPowerMa.toString())

        for (fn in spec.functions) {
            val instance = "$FUNCTION_PREFIX${spec.instance(fn.name)}"
            val functionPath = gadgetPath / "functions/$instance"
            try {
                mkdir(functionPath)
            } catch (e: ErrnoException) {
                if (e.errno == OsConstants.ENODEV) throw HidResourceExhaustedException(e)
                throw e
            }
            (functionPath / "protocol").writeText(fn.protocol.toString())
            (functionPath / "subclass").writeText(fn.subclass.toString())
            (functionPath / "report_length").writeText(fn.reportLength.toString())
            (functionPath / "report_desc").writeBytes(fn.reportDescriptor)
            linkFunction(configPath / instance, functionPath)
        }

        for (fn in spec.ffsFunctions) {
            val instance = "$FFS_PREFIX${spec.instance(fn.name)}"
            val functionPath = gadgetPath / "functions/$instance"
            functionPath.createDirectories()
            linkFunction(configPath / instance, functionPath)
        }

        spec.osDescVendorCode?.let { vendorCode ->
            val osDescPath = gadgetPath / "os_desc"
            (osDescPath / "use").writeText("1")
            (osDescPath / "b_vendor_code").writeText(hex8(vendorCode))
            (osDescPath / "qw_sign").writeText(OS_DESC_SIGNATURE)
            linkFunction(osDescPath / CONFIG_NAME, configPath)
        }
    }

    fun remove(name: String) {
        if (owner() == name) clearSlot()
    }

    private fun ensureSlot(): Path {
        if (!SLOT_PATH.isDirectory()) {
            try {
                mkdir(SLOT_PATH)
            } catch (e: ErrnoException) {
                if (e.errno == OsConstants.ENOMEM) throw GadgetSlotUnavailableException(e)
                throw e
            }
        }
        return SLOT_PATH
    }

    private fun clearSlot() {
        EndpointTable.closeAll()
        if (!SLOT_PATH.isDirectory()) return
        val devicePaths = slotDevicePaths().values
        children(SLOT_PATH / "os_desc").filter { it.isSymbolicLink() }.forEach { it.deleteIfExists() }
        runCatching { (SLOT_PATH / "os_desc/use").writeText("0") }
        for (config in children(SLOT_PATH / "configs")) {
            children(config).filter { it.isSymbolicLink() }.forEach { it.deleteIfExists() }
            children(config / "strings").forEach { it.deleteIfExists() }
            config.deleteIfExists()
        }
        for (fn in children(SLOT_PATH / "functions")) {
            if (fn.name.startsWith(FFS_PREFIX)) FfsMounter.unmount(fn.name.removePrefix(FFS_PREFIX))
            fn.deleteIfExists()
        }
        children(SLOT_PATH / "strings").forEach { it.deleteIfExists() }
        deleteDeviceNodes(devicePaths)
    }

    private fun unbindOwn() {
        val devicePaths = slotDevicePaths().values
        EndpointTable.closeAll(EndpointTable.Kind.HID)
        unbindUdc(SLOT)
        deleteDeviceNodes(devicePaths)
        EndpointTable.closeAll(EndpointTable.Kind.FFS)
    }

    private fun owner(): String? {
        val owners = children(SLOT_PATH / "functions").map { fn ->
            fn.name.substringAfter('.', "").substringBefore(GadgetSpec.INSTANCE_SEPARATOR, "")
                .takeIf { GadgetSpec.isValidName(it) } ?: UNKNOWN_OWNER
        }.toSet()
        return when {
            owners.isEmpty() -> null
            owners.size == 1 && UNKNOWN_OWNER !in owners -> owners.single()
            else -> {
                AppLog.w(TAG, "slot has unrecognized or mixed owners: $owners")
                UNKNOWN_OWNER
            }
        }
    }

    private fun deleteDeviceNodes(devicePaths: Collection<String>) {
        for (path in devicePaths) {
            runCatching { Path(path).deleteIfExists() }
                .onFailure { AppLog.w(TAG, "delete device node failed for $path", it) }
        }
    }

    fun queryGlobalState(): GlobalState {
        val owner = owner() ?: return GlobalState(null, false)
        return GlobalState(owner, isBound(SLOT))
    }

    private fun isBound(name: String): Boolean =
        !runCatching { (GADGET_ROOT / name / "UDC").readText() }.getOrNull()?.trim().isNullOrEmpty()

    fun openHid(name: String): Map<String, Long> {
        check(owner() == name && isBound(SLOT)) { "gadget $name not active" }
        EndpointTable.closeAll(EndpointTable.Kind.HID)
        val opened = mutableMapOf<String, Long>()
        try {
            for ((function, path) in slotDevicePaths()) {
                waitUntilExists(path)
                opened[function] = EndpointTable.open(path, EndpointTable.Kind.HID)
            }
        } catch (e: Exception) {
            opened.values.forEach(EndpointTable::close)
            throw e
        }
        return opened.toMap()
    }

    private fun waitUntilExists(path: String) {
        val deadline = System.currentTimeMillis() + NODE_WAIT_TIMEOUT_MS
        while (!Path(path).exists()) {
            check(System.currentTimeMillis() < deadline) { "timed out waiting for $path" }
            Thread.sleep(NODE_WAIT_INTERVAL_MS)
        }
    }

    private fun slotDevicePaths(): Map<String, String> =
        children(SLOT_PATH / "functions")
            .filter { it.name.startsWith(FUNCTION_PREFIX) }
            .mapNotNull { fn ->
                runCatching { (fn / "dev").readText().trim().substringAfter(':').toInt() }
                    .onFailure { AppLog.e(TAG, "read dev failed for ${fn.name}", it) }
                    .getOrNull()
                    ?.let { minor -> fn.name.removePrefix(FUNCTION_PREFIX).substringAfter(GadgetSpec.INSTANCE_SEPARATOR) to "/dev/hidg$minor" }
            }
            .toMap()

    fun deactivate(name: String) {
        if (owner() != name) return
        if (isBound(SLOT)) {
            unbindOwn()
        } else {
            EndpointTable.closeAll()
        }
    }

    fun prepareFfs(spec: GadgetSpec): Map<String, LongArray> {
        check(owner() == spec.name) { "gadget ${spec.name} not created" }
        check(!isBound(SLOT)) { "gadget ${spec.name} already bound" }
        EndpointTable.closeAll(EndpointTable.Kind.FFS)
        try {
            return spec.ffsFunctions.associate { fn -> fn.name to openFfs(spec.instance(fn.name), fn) }
        } catch (e: Exception) {
            EndpointTable.closeAll(EndpointTable.Kind.FFS)
            throw e
        }
    }

    private fun openFfs(instance: String, fn: FfsFunctionSpec): LongArray {
        check((SLOT_PATH / "functions/$FFS_PREFIX$instance").isDirectory()) { "function $instance missing" }
        val dir = FfsMounter.mount(instance)
        val ep0 = EndpointTable.open("$dir/ep0", EndpointTable.Kind.FFS)
        try {
            EndpointTable.writeExact(ep0, fn.descriptors)
        } catch (e: ErrnoException) {
            if (e.errno == OsConstants.EINVAL) throw FfsDescriptorsRejectedException(e)
            throw e
        }
        EndpointTable.writeExact(ep0, fn.strings)
        return longArrayOf(ep0) + LongArray(fn.endpointCount) { i -> EndpointTable.open("$dir/ep${i + 1}", EndpointTable.Kind.FFS) }
    }

    fun activate(name: String): Map<String, Long> {
        check(owner() == name) { "gadget $name not created" }
        unbindCurrentUdcOwner()
        writeUdc(SLOT, getPhysicalUdcName())
        return openHid(name)
    }

    private fun unbindCurrentUdcOwner() {
        for (path in children(GADGET_ROOT)) {
            if (!path.isDirectory() || !isBound(path.name)) continue
            if (path.name != SLOT) systemUdc = runCatching { (path / "UDC").readText().trim() }.getOrNull()
            unbindUdc(path.name)
        }
    }

    private fun hasLinkedFunction(gadgetPath: Path): Boolean =
        children(gadgetPath / "configs").any { config -> children(config).any { it.isSymbolicLink() } }

    fun restoreSystemUdc(): Boolean {
        val candidates = children(GADGET_ROOT)
            .filter { it.isDirectory() && it.name != SLOT && hasLinkedFunction(it) }
            .map { it.name }
        AppLog.i(TAG) { "restoreSystemUdc: candidates=$candidates" }
        if (candidates.isEmpty()) return true
        if (candidates.any { isBound(it) }) return true
        val physicalUdc = runCatching { getPhysicalUdcName() }.getOrElse {
            AppLog.w(TAG, "restoreSystemUdc: no physical UDC", it)
            return false
        }
        for (name in candidates) {
            val error = runCatching { writeUdc(name, physicalUdc) }.exceptionOrNull() ?: return true
            if ((error as? ErrnoException)?.errno == OsConstants.EBUSY) {
                AppLog.i(TAG) { "restoreSystemUdc: EBUSY on $name, already reclaimed by system" }
                return true
            }
            AppLog.w(TAG, "restoreSystemUdc: bind $name failed", error)
        }
        return false
    }

    fun configfsCheck(): EnvCheck {
        val present = GADGET_ROOT.isDirectory()
        return EnvCheck(EnvChecks.CONFIGFS, if (present) CheckStatus.OK else CheckStatus.FAIL, GADGET_ROOT.toString())
    }

    fun udcCheck(): EnvCheck {
        val names = children(UDC_CLASS_ROOT).map { it.name }.sorted()
        return EnvCheck(EnvChecks.UDC, if (names.any { !it.startsWith(DUMMY_UDC_PREFIX) }) CheckStatus.OK else CheckStatus.FAIL, names.joinToString())
    }

    fun slotCheck(): EnvCheck {
        if (!SLOT_PATH.isDirectory()) return EnvCheck(EnvChecks.SLOT, CheckStatus.INFO, "$SLOT: -")
        val owner = owner()
        val status = if (owner == UNKNOWN_OWNER) CheckStatus.WARN else CheckStatus.INFO
        val bound = runCatching { (SLOT_PATH / "UDC").readText().trim() }.getOrDefault("")
        return EnvCheck(EnvChecks.SLOT, status, "$SLOT: ${owner ?: "-"}" + if (bound.isNotEmpty()) " → $bound" else "")
    }

    fun systemGadgetsCheck(): EnvCheck {
        val gadgets = children(GADGET_ROOT).filter { it.isDirectory() && it.name != SLOT }.map { path ->
            val udc = runCatching { (path / "UDC").readText().trim() }.getOrDefault("")
            if (udc.isEmpty()) path.name else "${path.name} → $udc"
        }
        return EnvCheck(EnvChecks.SYSTEM_GADGETS, CheckStatus.INFO, gadgets.joinToString())
    }

    private fun getPhysicalUdcName(): String {
        val available = UDC_CLASS_ROOT.listDirectoryEntries().map { it.name }.sorted()
        require(available.isNotEmpty()) { "no UDC found under $UDC_CLASS_ROOT" }
        systemUdc?.takeIf { it in available }?.let { return it }
        return available.firstOrNull { !it.startsWith(DUMMY_UDC_PREFIX) } ?: available.first()
    }

    private fun configfsRoot(): Path =
        runCatching { Path("/proc/mounts").readLines() }.getOrDefault(emptyList())
            .map { it.split(' ') }
            .firstOrNull { it.getOrNull(2) == "configfs" }
            ?.let { Path(it[1]) }
            ?: Path("/config")

    private fun unbindUdc(name: String) = writeUdc(name, "\n")

    private fun linkFunction(link: Path, target: Path) {
        try {
            link.createSymbolicLinkPointingTo(target)
        } catch (e: FileAlreadyExistsException) {
            link.deleteIfExists()
            link.createSymbolicLinkPointingTo(target)
        }
    }

    private fun children(dir: Path): List<Path> =
        runCatching { dir.listDirectoryEntries() }.getOrDefault(emptyList())

    private fun hex16(value: Int): String = "0x%04x".format(value)

    private fun hex8(value: Int): String = "0x%02x".format(value)

    private fun mkdir(path: Path) {
        try {
            Os.mkdir(path.toString(), DIR_MODE)
        } catch (e: ErrnoException) {
            if (e.errno != OsConstants.EEXIST || !path.isDirectory()) throw e
        }
    }

    private fun writeUdc(name: String, value: String) {
        val fd = Os.open((GADGET_ROOT / name / "UDC").toString(), OsConstants.O_WRONLY or OsConstants.O_SYNC or OsConstants.O_CLOEXEC, 0)
        try {
            val bytes = value.toByteArray()
            val written = Os.write(fd, bytes, 0, bytes.size)
            check(written == bytes.size) { "short write to $name/UDC: $written/${bytes.size}" }
        } finally {
            Os.close(fd)
        }
    }
}
