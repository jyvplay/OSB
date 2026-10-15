package dev.fusion.notes

import android.util.AtomicFile
import dev.fusion.storage.CheckedAtomicFile
import java.io.File

/** Single owner only; callers serialize IO. Directory is app no_backup, not the saved-note store. */
class NotesRecoveryStore(directory: File) {
    private val file=AtomicFile(File(directory,"notes-recovery-v1.bin"))
    fun read(): NoteDraft {
        if(!file.baseFile.exists() && !File(file.baseFile.path+".bak").exists()) return NoteDraft()
        return file.openRead().use(NoteDraftCodec::read)
    }
    fun write(draft: NoteDraft) = CheckedAtomicFile.write(file,draft,NoteDraftCodec::write,NoteDraftCodec::read)
}
