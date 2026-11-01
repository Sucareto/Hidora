package app.sucareto.hidora.core.gadget

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import app.sucareto.hidora.util.AppLog
import java.io.FileDescriptor
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

object EndpointTable {
    enum class Kind { HID, FFS }

    private class Entry(val fd: FileDescriptor, val kind: Kind, val path: String) {
        val lock = ReentrantLock()
        val idle = lock.newCondition()
        var inFlight = 0
        @Volatile var closed = false
    }

    private const val TAG = "EndpointTable"
    private const val WRITE_ATTEMPTS = 5
    private const val WRITE_RETRY_DELAY_MS = 5L
    private const val POLL_SLICE_MS = 100
    private const val DRAIN_TIMEOUT_MS = 1000L
    private val entries = ConcurrentHashMap<Long, Entry>()
    private val nextHandle = AtomicLong(1)

    fun open(path: String, kind: Kind): Long {
        val fd = Os.open(path, OsConstants.O_RDWR or OsConstants.O_NONBLOCK or OsConstants.O_CLOEXEC, 0)
        val handle = nextHandle.getAndIncrement()
        entries[handle] = Entry(fd, kind, path)
        return handle
    }

    fun writeExact(handle: Long, data: ByteArray) {
        val result = write(handle, data)
        if (result < 0) throw ErrnoException("write", -result)
        check(result == data.size) { "short write $result/${data.size}" }
    }

    fun write(handle: Long, data: ByteArray): Int = use(handle) { entry -> writeRetrying(entry, data) }

    private fun writeRetrying(entry: Entry, data: ByteArray): Int {
        var attempt = 0
        while (true) {
            try {
                return Os.write(entry.fd, data, 0, data.size)
            } catch (e: ErrnoException) {
                val retry = entry.kind == Kind.HID && e.errno == OsConstants.EAGAIN && ++attempt < WRITE_ATTEMPTS
                if (!retry) return -e.errno
                Thread.sleep(WRITE_RETRY_DELAY_MS)
            }
        }
    }

    fun read(handle: Long, buffer: ByteArray): Int = use(handle) { entry ->
        try {
            Os.read(entry.fd, buffer, 0, buffer.size)
        } catch (e: ErrnoException) {
            -e.errno
        }
    }

    fun poll(handle: Long, timeoutMs: Int): Int = use(handle) { entry ->
        val pollFd = StructPollfd().apply {
            fd = entry.fd
            events = OsConstants.POLLIN.toShort()
        }
        val deadline = if (timeoutMs < 0) Long.MAX_VALUE else System.currentTimeMillis() + timeoutMs
        while (!entry.closed) {
            val slice = (deadline - System.currentTimeMillis()).coerceIn(0, POLL_SLICE_MS.toLong()).toInt()
            try {
                if (Os.poll(arrayOf(pollFd), slice) > 0) return@use pollFd.revents.toInt()
            } catch (e: ErrnoException) {
                if (e.errno != OsConstants.EINTR) return@use -e.errno
            }
            if (slice == 0) return@use 0
        }
        -OsConstants.EBADF
    }

    fun close(handle: Long) {
        entries.remove(handle)?.let(::closeEntry)
    }

    fun closeAll(kind: Kind? = null) {
        val removed = mutableListOf<Entry>()
        entries.entries.removeIf { (_, entry) -> (kind == null || entry.kind == kind).also { if (it) removed += entry } }
        removed.forEach(::closeEntry)
    }

    private inline fun use(handle: Long, block: (Entry) -> Int): Int {
        val entry = entries[handle] ?: return -OsConstants.EBADF
        entry.lock.withLock {
            if (entry.closed) return -OsConstants.EBADF
            entry.inFlight++
        }
        try {
            return block(entry)
        } finally {
            entry.lock.withLock {
                if (--entry.inFlight == 0) entry.idle.signalAll()
            }
        }
    }

    private fun closeEntry(entry: Entry) {
        val drained = entry.lock.withLock {
            entry.closed = true
            var remaining = TimeUnit.MILLISECONDS.toNanos(DRAIN_TIMEOUT_MS)
            while (entry.inFlight > 0 && remaining > 0) remaining = entry.idle.awaitNanos(remaining)
            entry.inFlight == 0
        }
        if (!drained) AppLog.w(TAG, "closing ${entry.path} with a call still in flight")
        runCatching { Os.close(entry.fd) }.onFailure { AppLog.w(TAG, "close ${entry.path} failed", it) }
    }
}
