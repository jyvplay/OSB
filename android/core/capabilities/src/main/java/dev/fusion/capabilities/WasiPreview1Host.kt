// SPDX-License-Identifier: Apache-2.0
package dev.fusion.capabilities

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.security.SecureRandom

/**
 * Clean-room WASI Preview 1 (`wasi_snapshot_preview1`) capability-sandboxed host kernel (`WasiPreview1Host`).
 *
 * Complements `:core:capabilities` [WasmSandboxKernel] (pure bytecode interpreter) by providing
 * the capability-gated POSIX-subset host ABI modeled after Bytecode Alliance WASI Preview 1 / Chicory 1.7.5:
 * - Zero ambient authority: a guest module receives ONLY explicit preopened directory descriptors (`FD >= 3`),
 *   explicitly passed `argv`/`environ`, and bounded `stdout`/`stderr` byte caps (`maxOutputBytes`).
 * - Path traversal firewall: `path_open` resolves relative paths strictly beneath the preopen virtual root;
 *   `..` parent escapes, leading `/`, NUL bytes, or missing `RIGHT_FD_READ`/`RIGHT_FD_WRITE` return
 *   exact WASI `ERRNO_NOTCAPABLE (76)` or `ERRNO_ACCES (2)`.
 * - Side-channel defense: `clock_time_get` quantizes monotonic/realtime timestamps to `clockQuantumNs`
 *   (default `100_000 ns` = `100 µs`) to defeat Wasm microarchitectural cache-timing attacks.
 */
object WasiPreview1Host {
    const val SCOPE =
        "Capability-gated WASI Preview 1 host kernel (args/environ, fd_read/fd_write, path_open with ERRNO_NOTCAPABLE .. escape defense, 100us-quantized clock_time_get, bounded CSPRNG random_get, proc_exit); sockets and threads out of scope."

    const val ERRNO_SUCCESS = 0
    const val ERRNO_ACCES = 2
    const val ERRNO_BADF = 8
    const val ERRNO_FAULT = 21
    const val ERRNO_INVAL = 28
    const val ERRNO_NOENT = 44
    const val ERRNO_NOSPC = 51
    const val ERRNO_NOTCAPABLE = 76

    const val RIGHT_FD_READ = 1L shl 1
    const val RIGHT_FD_WRITE = 1L shl 6
    const val RIGHT_PATH_OPEN = 1L shl 13

    data class PreopenDir(
        val guestMount: String,
        val rights: Long = RIGHT_FD_READ or RIGHT_PATH_OPEN,
        val files: MutableMap<String, ByteArray> = linkedMapOf(),
    )

    class HostInstance(
        val memorySize: Int = 65_536,
        val args: List<String> = listOf("app.wasm"),
        val envVars: Map<String, String> = emptyMap(),
        preopens: List<PreopenDir> = emptyList(),
        val maxOutputBytes: Int = 64 * 1024,
        val clockQuantumNs: Long = 100_000L,
        private val rng: SecureRandom = SecureRandom(),
    ) {
        val memory: ByteArray = ByteArray(memorySize)
        private val stdoutBuf = ArrayList<Byte>()
        private val stderrBuf = ArrayList<Byte>()
        var exitCode: Int? = null
            private set

        private sealed interface FdEntry {
            data class StdStream(val writable: Boolean, val isErr: Boolean) : FdEntry
            data class Preopen(val dir: PreopenDir) : FdEntry
            data class OpenFile(val dir: PreopenDir, val relPath: String, val rights: Long, var offset: Int = 0) : FdEntry
        }

        private val fdTable = linkedMapOf<Int, FdEntry>()
        private var nextFd = 3

        init {
            require(memorySize in 1024..(16 * 1024 * 1024)) { "Invalid WASI linear memory size: $memorySize" }
            require(clockQuantumNs >= 1_000L) { "clockQuantumNs must be >= 1_000 ns for timing side-channel defense" }
            fdTable[0] = FdEntry.StdStream(writable = false, isErr = false)
            fdTable[1] = FdEntry.StdStream(writable = true, isErr = false)
            fdTable[2] = FdEntry.StdStream(writable = true, isErr = true)
            for (p in preopens) {
                fdTable[nextFd++] = FdEntry.Preopen(p)
            }
        }

        fun stdoutUtf8(): String = ByteArray(stdoutBuf.size) { stdoutBuf[it] }.toString(StandardCharsets.UTF_8)
        fun stderrUtf8(): String = ByteArray(stderrBuf.size) { stderrBuf[it] }.toString(StandardCharsets.UTF_8)

        fun writeMem(ptr: Int, data: ByteArray): Boolean {
            if (ptr < 0 || ptr.toLong() + data.size > memory.size) return false
            System.arraycopy(data, 0, memory, ptr, data.size)
            return true
        }

        fun readI32(ptr: Int): Int =
            ByteBuffer.wrap(memory, ptr, 4).order(ByteOrder.LITTLE_ENDIAN).int

        fun readI64(ptr: Int): Long =
            ByteBuffer.wrap(memory, ptr, 8).order(ByteOrder.LITTLE_ENDIAN).long

        fun writeI32(ptr: Int, v: Int) {
            ByteBuffer.wrap(memory, ptr, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(v)
        }

        fun writeI64(ptr: Int, v: Long) {
            ByteBuffer.wrap(memory, ptr, 8).order(ByteOrder.LITTLE_ENDIAN).putLong(v)
        }

        fun argsSizesGet(argcPtr: Int, argvBufSizePtr: Int): Int {
            if (!inBounds(argcPtr, 4) || !inBounds(argvBufSizePtr, 4)) return ERRNO_FAULT
            val totalBytes = args.sumOf { it.toByteArray(StandardCharsets.UTF_8).size + 1 }
            writeI32(argcPtr, args.size)
            writeI32(argvBufSizePtr, totalBytes)
            return ERRNO_SUCCESS
        }

        fun argsGet(argvPtr: Int, argvBufPtr: Int): Int {
            val totalBytes = args.sumOf { it.toByteArray(StandardCharsets.UTF_8).size + 1 }
            if (!inBounds(argvPtr, args.size * 4) || !inBounds(argvBufPtr, totalBytes)) return ERRNO_FAULT
            var cursor = argvBufPtr
            args.forEachIndexed { idx, arg ->
                writeI32(argvPtr + idx * 4, cursor)
                val b = arg.toByteArray(StandardCharsets.UTF_8)
                System.arraycopy(b, 0, memory, cursor, b.size)
                memory[cursor + b.size] = 0
                cursor += b.size + 1
            }
            return ERRNO_SUCCESS
        }

        fun environSizesGet(countPtr: Int, bufSizePtr: Int): Int {
            if (!inBounds(countPtr, 4) || !inBounds(bufSizePtr, 4)) return ERRNO_FAULT
            val entries = envVars.entries.map { "${it.key}=${it.value}".toByteArray(StandardCharsets.UTF_8) }
            writeI32(countPtr, entries.size)
            writeI32(bufSizePtr, entries.sumOf { it.size + 1 })
            return ERRNO_SUCCESS
        }

        fun fdPrestatDirName(fd: Int, pathPtr: Int, pathLen: Int): Int {
            val entry = fdTable[fd] as? FdEntry.Preopen ?: return ERRNO_BADF
            val bytes = entry.dir.guestMount.toByteArray(StandardCharsets.UTF_8)
            if (pathLen < bytes.size || !inBounds(pathPtr, bytes.size)) return ERRNO_FAULT
            System.arraycopy(bytes, 0, memory, pathPtr, bytes.size)
            return ERRNO_SUCCESS
        }

        fun pathOpen(
            dirFd: Int,
            pathPtr: Int,
            pathLen: Int,
            requestedRights: Long,
            createIfMissing: Boolean,
            openedFdPtr: Int,
        ): Int {
            val preopen = fdTable[dirFd] as? FdEntry.Preopen ?: return ERRNO_BADF
            if ((preopen.dir.rights and RIGHT_PATH_OPEN) == 0L) return ERRNO_NOTCAPABLE
            if ((requestedRights and preopen.dir.rights.inv()) != 0L) return ERRNO_NOTCAPABLE
            if (!inBounds(pathPtr, pathLen) || !inBounds(openedFdPtr, 4)) return ERRNO_FAULT

            val rawPath = String(memory, pathPtr, pathLen, StandardCharsets.UTF_8)
            val norm = normalizeSafeRelative(rawPath) ?: return ERRNO_NOTCAPABLE

            if (!preopen.dir.files.containsKey(norm)) {
                if (!createIfMissing) return ERRNO_NOENT
                if ((requestedRights and RIGHT_FD_WRITE) == 0L) return ERRNO_ACCES
                preopen.dir.files[norm] = ByteArray(0)
            }
            val newFd = nextFd++
            fdTable[newFd] = FdEntry.OpenFile(preopen.dir, norm, requestedRights)
            writeI32(openedFdPtr, newFd)
            return ERRNO_SUCCESS
        }

        fun fdWrite(fd: Int, iovsPtr: Int, iovsLen: Int, nwrittenPtr: Int): Int {
            if (iovsLen < 0 || iovsLen > 1024 || !inBounds(iovsPtr, iovsLen * 8) || !inBounds(nwrittenPtr, 4)) {
                return ERRNO_FAULT
            }
            val entry = fdTable[fd] ?: return ERRNO_BADF
            var written = 0
            for (i in 0 until iovsLen) {
                val bufPtr = readI32(iovsPtr + i * 8)
                val bufLen = readI32(iovsPtr + i * 8 + 4)
                if (bufLen < 0 || !inBounds(bufPtr, bufLen)) return ERRNO_FAULT
                when (entry) {
                    is FdEntry.StdStream -> {
                        if (!entry.writable) return ERRNO_BADF
                        val target = if (entry.isErr) stderrBuf else stdoutBuf
                        if (target.size + bufLen > maxOutputBytes) return ERRNO_NOSPC
                        for (k in 0 until bufLen) target.add(memory[bufPtr + k])
                        written += bufLen
                    }
                    is FdEntry.OpenFile -> {
                        if ((entry.rights and RIGHT_FD_WRITE) == 0L) return ERRNO_NOTCAPABLE
                        val existing = entry.dir.files[entry.relPath] ?: ByteArray(0)
                        if (entry.offset + bufLen > maxOutputBytes) return ERRNO_NOSPC
                        val next = ByteArray(maxOf(existing.size, entry.offset + bufLen))
                        System.arraycopy(existing, 0, next, 0, existing.size)
                        System.arraycopy(memory, bufPtr, next, entry.offset, bufLen)
                        entry.dir.files[entry.relPath] = next
                        entry.offset += bufLen
                        written += bufLen
                    }
                    is FdEntry.Preopen -> return ERRNO_INVAL
                }
            }
            writeI32(nwrittenPtr, written)
            return ERRNO_SUCCESS
        }

        fun fdRead(fd: Int, iovsPtr: Int, iovsLen: Int, nreadPtr: Int): Int {
            if (iovsLen < 0 || iovsLen > 1024 || !inBounds(iovsPtr, iovsLen * 8) || !inBounds(nreadPtr, 4)) {
                return ERRNO_FAULT
            }
            val entry = fdTable[fd] as? FdEntry.OpenFile ?: return ERRNO_BADF
            if ((entry.rights and RIGHT_FD_READ) == 0L) return ERRNO_NOTCAPABLE
            val data = entry.dir.files[entry.relPath] ?: return ERRNO_NOENT
            var totalRead = 0
            for (i in 0 until iovsLen) {
                val bufPtr = readI32(iovsPtr + i * 8)
                val bufLen = readI32(iovsPtr + i * 8 + 4)
                if (bufLen < 0 || !inBounds(bufPtr, bufLen)) return ERRNO_FAULT
                val avail = (data.size - entry.offset).coerceAtLeast(0)
                val toCopy = minOf(avail, bufLen)
                if (toCopy > 0) {
                    System.arraycopy(data, entry.offset, memory, bufPtr, toCopy)
                    entry.offset += toCopy
                    totalRead += toCopy
                }
                if (toCopy < bufLen) break
            }
            writeI32(nreadPtr, totalRead)
            return ERRNO_SUCCESS
        }

        fun clockTimeGet(rawNowNs: Long, timeOutPtr: Int): Int {
            if (!inBounds(timeOutPtr, 8)) return ERRNO_FAULT
            val quantized = (rawNowNs / clockQuantumNs) * clockQuantumNs
            writeI64(timeOutPtr, quantized)
            return ERRNO_SUCCESS
        }

        fun randomGet(bufPtr: Int, bufLen: Int): Int {
            if (bufLen < 0 || bufLen > 65_536 || !inBounds(bufPtr, bufLen)) return ERRNO_FAULT
            val tmp = ByteArray(bufLen)
            rng.nextBytes(tmp)
            System.arraycopy(tmp, 0, memory, bufPtr, bufLen)
            return ERRNO_SUCCESS
        }

        fun procExit(code: Int): Int {
            exitCode = code
            return ERRNO_SUCCESS
        }

        private fun inBounds(ptr: Int, len: Int): Boolean =
            ptr >= 0 && len >= 0 && ptr.toLong() + len.toLong() <= memory.size.toLong()

        private fun normalizeSafeRelative(raw: String): String? {
            if (raw.isEmpty() || raw.contains('\u0000') || raw.contains('\\') || raw.startsWith("/")) return null
            val parts = ArrayList<String>()
            for (seg in raw.split('/')) {
                if (seg.isEmpty() || seg == ".") continue
                if (seg == "..") {
                    if (parts.isEmpty()) return null // Escapes preopen mount!
                    parts.removeAt(parts.lastIndex)
                } else {
                    parts.add(seg)
                }
            }
            return if (parts.isEmpty()) null else parts.joinToString("/")
        }
    }
}
