// SPDX-License-Identifier: Apache-2.0
package dev.fusion.files

import java.net.URI

/**
 * Clean-room USB / SD / SMB / SFTP / WebDAV / FTP Mount Descriptor Validator, Exponential-Backoff
 * Reconnect Diagnostics Planner (`FM-08`), and Generation-Stamped Cached Background Folder-Size
 * Aggregator (`FM-17`).
 */
object RemoteMountReconnectPlanner {
    const val SCOPE =
        "Pure-JVM USB/SD/SMB/SFTP/WebDAV/FTP mount validator, reconnect backoff diagnostics (FM-08), and generation-stamped cached folder-size tree (FM-17)."

    enum class MountProtocol { USB_OTG, SD_REMOVABLE, SMB3, SFTP, WEBDAVS, FTPS }

    data class MountDiagnostics(
        val protocol: MountProtocol,
        val hostOrVolume: String,
        val encryptedTransport: Boolean,
        val nextRetryDelayMs: Long,
        val diagnosticCode: String,
    )

    fun parseAndPlanReconnect(
        endpointUri: String,
        consecutiveFailures: Int,
        lastErrorKind: String = "NONE",
    ): MountDiagnostics {
        require(consecutiveFailures >= 0) { "negative failure count" }
        val u = URI(endpointUri.trim())
        val scheme = u.scheme?.lowercase() ?: throw IllegalArgumentException("missing mount scheme")
        val (proto, encrypted) = when (scheme) {
            "usb" -> MountProtocol.USB_OTG to true
            "sd" -> MountProtocol.SD_REMOVABLE to true
            "smb" -> MountProtocol.SMB3 to true
            "sftp" -> MountProtocol.SFTP to true
            "davs", "https" -> MountProtocol.WEBDAVS to true
            "ftps" -> MountProtocol.FTPS to true
            "ftp", "dav", "http" -> throw IllegalArgumentException("cleartext remote filesystem '$scheme://' refused; require sftp/davs/ftps/smb3")
            else -> throw IllegalArgumentException("unsupported mount protocol '$scheme'")
        }
        val target = (u.host ?: u.authority ?: u.path)?.trim('/')?.takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("empty mount authority/volume")
        val delay = if (consecutiveFailures == 0) 0L
        else (500L * (1L shl consecutiveFailures.coerceAtMost(6))).coerceAtMost(30_000L)
        val diag = if (consecutiveFailures == 0) "MOUNT_HEALTHY" else "RECONNECT_${lastErrorKind.uppercase()}_ATTEMPT_$consecutiveFailures"
        return MountDiagnostics(proto, target, encrypted, delay, diag)
    }

    class CachedFolderSizeTree {
        private data class Entry(val bytes: Long, val generation: Long)
        private val fileSizes = HashMap<String, Long>()
        private val dirCache = HashMap<String, Entry>()
        private var generation: Long = 1L

        fun upsertFile(canonicalPath: String, bytes: Long) {
            require(bytes >= 0L && canonicalPath.startsWith("/")) { "invalid path or size" }
            fileSizes[canonicalPath] = bytes
            generation++
        }

        fun removeFile(canonicalPath: String) {
            if (fileSizes.remove(canonicalPath) != null) generation++
        }

        fun folderBytes(dirPath: String): Pair<Long, Boolean> {
            val norm = if (dirPath == "/") "/" else dirPath.trimEnd('/')
            val hit = dirCache[norm]
            if (hit != null && hit.generation == generation) return hit.bytes to true
            val prefix = if (norm == "/") "/" else "$norm/"
            var sum = 0L
            for ((p, sz) in fileSizes) {
                if (p.startsWith(prefix)) sum += sz
            }
            dirCache[norm] = Entry(sum, generation)
            return sum to false
        }
    }
}
