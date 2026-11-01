package app.sucareto.hidora.core.gadget

import android.os.Bundle
import com.topjohnwu.superuser.Shell
import java.util.zip.GZIPInputStream
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.isDirectory
import kotlin.io.path.readText

enum class CheckStatus { OK, WARN, FAIL, INFO }

class EnvCheck(val id: String, val status: CheckStatus, val detail: String)

object EnvChecks {
    const val DEVICE = "device"
    const val ROOT_ACCESS = "root_access"
    const val ROOT_SOLUTION = "root_solution"
    const val CONFIGFS = "configfs"
    const val UDC = "udc"
    const val HID_FUNCTION = "hid_function"
    const val FUNCTIONFS = "functionfs"
    const val SLOT = "slot"
    const val SYSTEM_GADGETS = "system_gadgets"
    const val KERNEL = "kernel"

    private const val KEY_IDS = "check_ids"
    private const val KEY_STATES = "check_states"
    private const val KEY_DETAILS = "check_details"

    fun toBundle(checks: List<EnvCheck>, target: Bundle) {
        target.putStringArray(KEY_IDS, checks.map { it.id }.toTypedArray())
        target.putStringArray(KEY_STATES, checks.map { it.status.name }.toTypedArray())
        target.putStringArray(KEY_DETAILS, checks.map { it.detail }.toTypedArray())
    }

    fun fromBundle(b: Bundle): List<EnvCheck> {
        val ids = b.getStringArray(KEY_IDS).orEmpty()
        val states = b.getStringArray(KEY_STATES).orEmpty()
        val details = b.getStringArray(KEY_DETAILS).orEmpty()
        return ids.indices.map { i ->
            EnvCheck(ids[i], states.getOrNull(i)?.let { s -> CheckStatus.entries.firstOrNull { it.name == s } } ?: CheckStatus.INFO, details.getOrElse(i) { "" })
        }
    }
}

object RootEnvironment {
    private const val KCONFIG = "/proc/config.gz"
    private const val HID_CONFIG = "CONFIG_USB_CONFIGFS_F_HID"
    private const val HID_MODULE = "/sys/module/usb_f_hid"
    private const val FILESYSTEMS = "/proc/filesystems"
    private const val FUNCTIONFS = "functionfs"

    fun run(): List<EnvCheck> = listOf(
        rootSolution(),
        GadgetOperations.configfsCheck(),
        GadgetOperations.udcCheck(),
        hidFunction(),
        functionFs(),
        GadgetOperations.slotCheck(),
        GadgetOperations.systemGadgetsCheck()
    )

    private fun rootSolution(): EnvCheck {
        val name = when {
            Path("/data/adb/ksud").exists() || Path("/data/adb/ksu").isDirectory() -> "KernelSU"
            Path("/data/adb/apd").exists() || Path("/data/adb/ap").isDirectory() -> "APatch"
            Path("/data/adb/magisk").isDirectory() -> "Magisk"
            else -> null
        }
        val version = Shell.cmd("su -v").exec().out.firstOrNull()?.trim().orEmpty()
        val detail = listOfNotNull(name, version.takeIf { it.isNotEmpty() }).joinToString(" · ")
        return EnvCheck(EnvChecks.ROOT_SOLUTION, if (name == "Magisk") CheckStatus.OK else CheckStatus.WARN, detail)
    }

    private fun hidFunction(): EnvCheck {
        if (Path(HID_MODULE).isDirectory()) return EnvCheck(EnvChecks.HID_FUNCTION, CheckStatus.OK, "usb_f_hid")
        val line = runCatching {
            GZIPInputStream(Path(KCONFIG).inputStream()).bufferedReader().useLines { lines ->
                lines.firstOrNull { it.startsWith("$HID_CONFIG=") || it == "# $HID_CONFIG is not set" }.orEmpty()
            }
        }.getOrNull() ?: return EnvCheck(EnvChecks.HID_FUNCTION, CheckStatus.INFO, "")
        val status = when (line.substringAfter('=', "")) {
            "y", "m" -> CheckStatus.OK
            else -> CheckStatus.FAIL
        }
        return EnvCheck(EnvChecks.HID_FUNCTION, status, line.ifEmpty { "$HID_CONFIG ?" })
    }

    private fun functionFs(): EnvCheck {
        val supported = runCatching { Path(FILESYSTEMS).readText().lineSequence().any { it.trim().endsWith(FUNCTIONFS) } }.getOrDefault(false)
        return EnvCheck(EnvChecks.FUNCTIONFS, if (supported) CheckStatus.OK else CheckStatus.WARN, if (supported) FUNCTIONFS else "")
    }
}
