package dev.fusion.storage

import android.util.AtomicFile
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * SH-08 / FF-15 / PR-09. Caller MUST serialize reads/writes with a single-owner mutex.
 * AOSP AtomicFile.finishWrite logs some failures instead of throwing. Explicit fd.sync
 * plus decoded readback avoids reporting a checkpoint merely because finishWrite returned.
 * T must have structural value equality (all current callers use immutable value models).
 * This is not a proof of power-loss durability or tamper protection.
 */
object CheckedAtomicFile {
    fun <T> write(file: AtomicFile, value: T, encode: (T, OutputStream) -> Unit, decode: (InputStream) -> T) {
        val stream = file.startWrite()
        try {
            encode(value,stream)
            stream.flush()
            stream.fd.sync() // IOException is propagated, unlike AtomicFile's logged sync failure.
            file.finishWrite(stream)
        } catch (e: Exception) {
            file.failWrite(stream)
            throw e
        }
        val reopened = file.openRead().use(decode)
        if (reopened != value) throw IOException("Checkpoint readback differs; persistence is not confirmed")
    }
}
