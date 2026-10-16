package dev.fusion.shell
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.fusion.automation.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class R19TaskStoreDeviceTest {
    @get:Rule val disposable=DisposableEmulatorRule()
    private fun fixture(body:(File)->Unit) {
        val dir=File(ApplicationProvider.getApplicationContext<Context>().cacheDir,"r19-"+UUID.randomUUID());check(dir.mkdir())
        try {body(File(dir,"tasks"))} finally {dir.deleteRecursively()}
    }
    @Test fun editUndoReopensAtNewRevisionAndReplayDoesNotWrite()=fixture {f ->
        val repo=TaskRepository(f);val before=repo.add(0,"Original",java.time.LocalDate.of(2028,2,29))
        val commit=repo.commitUndoable(1) {it.edit(before.tasks.single().id,"Edited",null)}
        assertEquals(2L,TaskRepository(f).load().revision)
        val undo=repo.undo(requireNotNull(commit.undo));assertEquals(3L,undo.revision);assertEquals(before.tasks,undo.tasks)
        assertEquals(before.tasks,TaskRepository(f).load().tasks)
        val bytes=f.readBytes();assertTrue(runCatching {repo.undo(commit.undo!!)}.exceptionOrNull() is TaskConflict)
        assertArrayEquals(bytes,f.readBytes())
    }
    @Test fun staleMergeAndSameRevisionDifferentDataRefuseWithoutWriting()=fixture {f ->
        val repo=TaskRepository(f);val before=repo.add(0,"Original",null)
        val incoming=TaskBook(500,listOf(LocalTask(UUID(0,9).toString(),"Imported")))
        val preview=TaskMergePreview.inspect(before,incoming)
        repo.mark(1,before.tasks.single().id,true);val current=f.readBytes()
        assertTrue(runCatching {repo.merge(preview)}.exceptionOrNull() is TaskConflict);assertArrayEquals(current,f.readBytes())
        // Actual external file rewrite is an adversarial input, not a mocked repository.
        f.writeBytes(TaskFrame.encode(TaskBook(1,before.tasks.map {it.copy(title="Different content")})))
        val altered=f.readBytes();assertTrue(runCatching {repo.merge(preview)}.exceptionOrNull() is TaskConflict)
        assertArrayEquals(altered,f.readBytes())
    }
    @Test fun fullArchivedImportIgnoresForeignRevisionAndCanBeUndone()=fixture {f ->
        val incoming=TaskBook(Long.MAX_VALUE-128,(0 until 128).map {LocalTask(UUID(0,it.toLong()).toString(),"Archived $it",archived=true)})
        val repo=TaskRepository(f);val before=repo.load();val preview=TaskMergePreview.inspect(before,incoming)
        assertFalse(f.exists()) // Preview has not published anything.
        val result=repo.merge(preview);assertEquals(1L,result.book.revision);assertEquals(incoming.tasks,TaskRepository(f).load().tasks)
        val undone=repo.undo(result.undo!!);assertEquals(2L,undone.revision);assertTrue(undone.tasks.isEmpty())
        assertTrue(TaskRepository(f).load().tasks.isEmpty())
    }
    @Test fun undoHeadroomFailureLeavesBytesAndLegacyRestoreBoundaryUsable()=fixture {f ->
        val task=LocalTask(UUID(0,1).toString(),"Reserved",archived=true)
        f.writeBytes(TaskFrame.encode(TaskBook(Long.MAX_VALUE-1,listOf(task))));val before=f.readBytes();val repo=TaskRepository(f)
        assertTrue(runCatching {repo.commitUndoable(Long.MAX_VALUE-1) {it.archive(task.id,false)}}.isFailure)
        assertArrayEquals(before,f.readBytes())
        assertEquals(Long.MAX_VALUE,repo.archive(Long.MAX_VALUE-1,task.id,false).revision)
        assertFalse(TaskRepository(f).load().tasks.single().archived)
    }
}
