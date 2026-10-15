package dev.fusion.notes

import android.util.AtomicFile
import dev.fusion.storage.CheckedAtomicFile
import java.io.File
import java.nio.channels.FileChannel
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.StandardOpenOption.*

/**
 * Each owner must read before writing. Cooperating owners share an OS lock and compare the
 * last observed value (including absence) before replacement. No blind last-writer wins.
 * Content-based conflict detection, not authenticated history, power-loss proof or arbitrary-writer CAS.
 */
class NotesRecoveryStore(directory: File) {
    private val root=directory.toPath()
    private val file=AtomicFile(File(directory,"notes-recovery-v1.bin"))
    private var observed=false
    private var expected:NoteDraft?=null
    companion object { private val processGate=Any() }
    private fun <T> locked(action:()->T):T = synchronized(processGate) {
        Files.createDirectories(root)
        require(Files.isDirectory(root,NOFOLLOW_LINKS)) { "Recovery directory must not be a symlink" }
        FileChannel.open(root.resolve(".notes-recovery.lock"),CREATE,WRITE,NOFOLLOW_LINKS).use { channel ->
            val lock=try { channel.tryLock() } catch(_:OverlappingFileLockException) { throw NotesBusy() }
            if(lock==null) throw NotesBusy()
            lock.use { action() }
        }
    }
    private fun current():NoteDraft? {
        if(!file.baseFile.exists() && !File(file.baseFile.path+".bak").exists()) return null
        return file.openRead().use(NoteDraftCodec::read)
    }
    fun read():NoteDraft = locked {
        val value=current();expected=value;observed=true
        value ?: NoteDraft()
    }
    fun write(draft:NoteDraft) = locked {
        check(observed) { "Read the current recovery state before writing" }
        if(current()!=expected) throw NoteConflict("Recovery changed in another owner; checkpoint refused. Preserve this draft or save explicitly before reopening Notes.")
        CheckedAtomicFile.write(file,draft,NoteDraftCodec::write,NoteDraftCodec::read)
        expected=draft
    }
}
