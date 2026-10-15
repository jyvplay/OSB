package dev.fusion.notes

import android.content.Context

/** Android directory adapter. Call only on an IO dispatcher; all file policy lives in NoteFiles. */
class NotesStore(context: Context) {
    private val files = NoteFiles(context.filesDir.resolve("notes").toPath())
    fun list() = files.list()
    fun trash() = files.trash()
    fun restore(entry: TrashedNote) = files.restore(entry)
    fun links() = files.links()
    fun load(name: String) = files.load(name)
    fun save(name: String, text: String, expected: NoteRevision?) = files.save(name, text, expected)
    fun delete(name: String, expected: NoteRevision) = files.delete(name, expected)
}
