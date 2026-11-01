package app.sucareto.hidora.core.ffs

import com.topjohnwu.superuser.Shell
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.readLines

object FfsMounter {
    private const val MOUNT_ROOT = "/dev/hidora-ffs"
    private const val UMOUNT_ATTEMPTS = 3
    private const val UMOUNT_RETRY_MS = 300L

    private fun mountPath(instance: String) = "$MOUNT_ROOT/$instance"

    fun mount(instance: String): String {
        val dir = mountPath(instance)
        if (isMounted(dir)) return dir
        Path(dir).createDirectories()
        check(Shell.cmd("mount -t functionfs $instance $dir").exec().isSuccess) { "mount functionfs $instance failed" }
        return dir
    }

    fun unmount(instance: String) {
        val dir = mountPath(instance)
        var attempt = 0
        while (isMounted(dir) && !Shell.cmd("umount $dir").exec().isSuccess) {
            check(++attempt < UMOUNT_ATTEMPTS) { "umount $dir failed" }
            Thread.sleep(UMOUNT_RETRY_MS)
        }
        Path(dir).deleteIfExists()
    }

    private fun isMounted(dir: String): Boolean =
        runCatching { Path("/proc/mounts").readLines().any { it.split(' ').getOrNull(1) == dir } }.getOrDefault(false)
}
