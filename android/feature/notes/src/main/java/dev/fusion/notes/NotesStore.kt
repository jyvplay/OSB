package dev.fusion.notes

import android.content.Context
import java.io.File

/** Local-first notes in app-private storage. No sync, no cloud. */
class NotesStore(context: Context) {
    private val dir = File(context.filesDir, "notes").apply { mkdirs() }
    fun list(): List<String> = dir.listFiles()?.map { it.nameWithoutExtension }?.sorted() ?: emptyList()
    fun load(name: String): String {
        val f = File(dir, "$name.md")
        return if (f.exists()) f.readText() else ""
    }
    fun save(name: String, text: String) {
        require(name.isNotBlank() && name.all { it.isLetterOrDigit() || it == '-' || it == '_' }) { "Use letters, digits, - _" }
        File(dir, "$name.md").writeText(text)
    }
    fun delete(name: String): Boolean = File(dir, "$name.md").delete()
}
