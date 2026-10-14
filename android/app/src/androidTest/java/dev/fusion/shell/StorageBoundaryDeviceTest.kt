package dev.fusion.shell

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.fusion.storage.FusionDb
import dev.fusion.notes.NoteFiles
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Actual Android SQLite/filesystem obligations, AUTHORED/UNRUN. Only fresh test paths. */
@RunWith(AndroidJUnit4::class)
class StorageBoundaryDeviceTest {
    @Test fun unsupportedUpgradeDoesNotDropRows() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val path=File(context.cacheDir,"migration-test-${UUID.randomUUID()}.db")
        val db=SQLiteDatabase.openOrCreateDatabase(path,null)
        val helper=FusionDb(context)
        try {
            helper.onCreate(db)
            db.execSQL("INSERT INTO session(key,value,updated) VALUES('owner','retained',1)")
            try { helper.onUpgrade(db,1,2); fail("Unsupported upgrade must refuse") }
            catch(_:SQLiteException) { /* Inspect the actual prior rows next. */ }
            db.rawQuery("SELECT value FROM session WHERE key='owner'",null).use {
                assertTrue(it.moveToFirst()); assertEquals("retained",it.getString(0))
            }
        } finally { db.close(); helper.close(); SQLiteDatabase.deleteDatabase(path) }
    }
    @Test fun nativeFilesystemSupportsConfirmedNotePublication() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val directory=File(context.cacheDir,"notes-test-${UUID.randomUUID()}")
        try {
            val notes=NoteFiles(directory.toPath())
            val first=notes.save("note","Native UTF-8 α",null)
            assertEquals(first,notes.load("note"))
            val changed=notes.save("note","second",first.revision)
            assertEquals(changed,notes.load("note"))
            notes.delete("note",changed.revision)
            assertTrue(notes.list().isEmpty())
        } finally { directory.deleteRecursively() }
    }
}
