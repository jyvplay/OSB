package dev.fusion.backup

import android.content.Context
import android.net.Uri
import dev.fusion.security.PasswordCrypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class BackupReport(
    val fileCount: Int,
    val plainBytes: Long,
    val sealedBytes: Long,
    val sha256: String
)

/**
 * Versioned export of app-private data (currently the notes directory).
 *
 * Honest limits:
 *  - Only app-private files are included. SAF trees are NOT copied; the app
 *    does not hold a durable grant to other people's folders.
 *  - Encryption is password-based (PBKDF2 + AES-GCM) so the archive is
 *    restorable on another device. Lose the password and the data is gone.
 *  - Restore refuses unsafe entry names (zip-slip) and never deletes existing
 *    files: a name clash is written as "name (restored N)".
 */
class BackupEngine(private val context: Context) {

    private fun notesDir(): File = File(context.filesDir, "notes").apply { mkdirs() }

    suspend fun export(target: Uri, password: CharArray): Result<BackupReport> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(password.size >= 8) { "Use a password of at least 8 characters" }
                val files = notesDir().listFiles()?.filter { it.isFile } ?: emptyList()
                val raw = ByteArrayOutputStream()
                ZipOutputStream(raw).use { zip ->
                    zip.putNextEntry(ZipEntry("fusion-manifest.txt"))
                    zip.write(
                        ("fusion-backup v1\nfiles=${files.size}\ncreated=${System.currentTimeMillis()}\n")
                            .toByteArray()
                    )
                    zip.closeEntry()
                    files.forEach { f ->
                        zip.putNextEntry(ZipEntry("notes/${f.name}"))
                        f.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
                val plain = raw.toByteArray()
                val sealed = PasswordCrypto.encrypt(password, plain)
                context.contentResolver.openOutputStream(target, "wt").use { out ->
                    requireNotNull(out) { "Cannot write to the chosen file" }
                    out.write(sealed)
                    out.flush()
                }
                BackupReport(
                    fileCount = files.size,
                    plainBytes = plain.size.toLong(),
                    sealedBytes = sealed.size.toLong(),
                    sha256 = sha256Hex(sealed)
                )
            }
        }

    suspend fun restore(source: Uri, password: CharArray): Result<Int> =
        withContext(Dispatchers.IO) {
            runCatching {
                val sealed = context.contentResolver.openInputStream(source).use { input ->
                    requireNotNull(input) { "Cannot read the chosen file" }
                    input.readBytes()
                }
                // Fails closed on a wrong password or a tampered archive.
                val plain = PasswordCrypto.decrypt(password, sealed)
                var restored = 0
                val dir = notesDir()
                ZipInputStream(plain.inputStream()).use { zip ->
                    var entry: ZipEntry? = zip.nextEntry
                    while (entry != null) {
                        val name = entry.name
                        if (!entry.isDirectory && name.startsWith("notes/")) {
                            val leaf = name.removePrefix("notes/")
                            require(!leaf.contains("/") && !leaf.contains("..")) { "Unsafe entry: $name" }
                            var out = File(dir, leaf)
                            var n = 1
                            while (out.exists()) {
                                out = File(dir, "${leaf.substringBeforeLast('.')} (restored $n).md")
                                n++
                            }
                            out.outputStream().use { zip.copyTo(it) }
                            restored++
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
                restored
            }
        }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }.take(32)
}
