package dev.fusion.shell

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.fusion.automation.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Base64
import java.util.UUID

class R18TaskDeviceTest {
    @get:Rule val disposable=DisposableEmulatorRule()
    private val legacy=Base64.getDecoder().decode("RlRTMQAAAAAAAAAHAAAAATAwMDAwMDAwLTAwMDAtMDAwMC0wMDAwLTAwMDAwMDAwMDAwMQEAC0xlZ2FjeSDwn5mCAAAAAAAAUvt+O7AB")
    private fun fixture(test:(File)->Unit) {
        val context=ApplicationProvider.getApplicationContext<Context>();val dir=File(context.cacheDir,"r18-"+UUID.randomUUID());check(dir.mkdir())
        try {test(File(dir,"tasks"))} finally {dir.deleteRecursively()}
    }
    @Test fun legacyLoadAndNoOpPreserveBytesButExplicitEditWritesVersionTwo() = fixture {f ->
        f.writeBytes(legacy);val repo=TaskRepository(f);val old=repo.load();val t=old.tasks.single()
        assertArrayEquals(legacy,f.readBytes());assertEquals(7L,repo.edit(7,t.id,t.title,t.due).revision)
        assertArrayEquals(legacy,f.readBytes())
        val next=repo.edit(7,t.id,"Edited legacy",null);assertEquals(8L,next.revision)
        assertEquals("FTS2",f.readBytes().copyOf(4).toString(Charsets.US_ASCII))
        assertEquals(next.tasks,TaskRepository(f).load().tasks);assertEquals(t.id,next.tasks.single().id);assertTrue(next.tasks.single().done)
    }
    @Test fun archiveSurvivesReopenRestorePreservesIdentityAndStaleEditCannotWin() = fixture {f ->
        val a=TaskRepository(f);val b=TaskRepository(f);val book=a.add(0,"Keep date and completion",java.time.LocalDate.of(2028,2,29));val t=book.tasks.single()
        b.mark(1,t.id,true);val archived=a.archive(2,t.id,true)
        val disk=TaskRepository(f).load();assertEquals(archived.tasks,disk.tasks);assertTrue(disk.tasks.single().archived)
        assertTrue(runCatching {b.edit(2,t.id,"stale",null)}.exceptionOrNull() is TaskConflict)
        assertTrue(runCatching {b.edit(3,t.id,"archived edit",null)}.exceptionOrNull() is IllegalArgumentException)
        assertEquals(3L,b.load().revision)
        val restored=b.archive(3,t.id,false);assertEquals(t.copy(done=true),restored.tasks.single())
        assertEquals(restored.tasks,TaskRepository(f).load().tasks)
    }
    @Test fun allArchivedStillCountTowardCapacityAndRestoreNeedsNoFreeSlot() = fixture {f ->
        val tasks=(0 until 128).map {LocalTask(UUID(0,it.toLong()).toString(),"Task $it",archived=true)}
        f.writeBytes(TaskFrame.encode(TaskBook(128,tasks)));val repo=TaskRepository(f);val before=f.readBytes()
        assertTrue(runCatching {repo.add(128,"overflow",null)}.isFailure);assertArrayEquals(before,f.readBytes())
        assertEquals(129L,repo.archive(128,tasks[0].id,false).revision);assertEquals(128,repo.load().tasks.size)
    }
}
