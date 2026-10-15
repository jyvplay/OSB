package dev.fusion.shell

import android.content.Context
import android.util.AtomicFile
import androidx.test.core.app.ApplicationProvider
import dev.fusion.automation.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class R17TaskStoreDeviceTest {
    @get:Rule val disposable=DisposableEmulatorRule()
    private fun fixture(test:(File)->Unit) {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val dir=File(context.cacheDir,"r17-tasks-"+UUID.randomUUID());check(dir.mkdir())
        try {test(File(dir,"book"))} finally {dir.deleteRecursively()}
    }
    @Test fun actualAtomicFileReopenAndConcurrentStaleWriterRefusal() = fixture { file ->
        val a=TaskRepository(file);val b=TaskRepository(file)
        val first=a.add(0,"Real local task 🙂",LocalDate.of(2028,2,29));assertEquals(first.tasks,b.load().tasks)
        val second=b.mark(1,first.tasks.single().id,true);assertTrue(TaskRepository(file).load().tasks.single().done)
        val start=CountDownLatch(1);val pool=Executors.newFixedThreadPool(2)
        try {
            val futures=listOf(a,b).mapIndexed { i,repo -> pool.submit<String> {
                start.await();try {repo.add(second.revision,"Writer $i",null);"OK"} catch(_:TaskConflict) {"CONFLICT"}
            } }
            start.countDown();assertEquals(setOf("OK","CONFLICT"),futures.map {it.get(5,TimeUnit.SECONDS)}.toSet())
            assertEquals(3L,a.load().revision);assertEquals(2,a.load().tasks.size)
        } finally {pool.shutdownNow()}
    }
    @Test fun corruptBaseRefusesMutationAndPreservesExactBytes() = fixture {file ->
        val bad="Not a task frame".toByteArray();file.writeBytes(bad)
        assertTrue(runCatching {TaskRepository(file).load()}.isFailure)
        assertTrue(runCatching {TaskRepository(file).add(0,"must not overwrite",null)}.isFailure)
        assertArrayEquals(bad,file.readBytes())
    }
    @Test fun oversizedBaseAndOrphanUnpublishedWriteRefuse() = fixture {file ->
        file.writeBytes(ByteArray(TaskFrame.MAX_BYTES+1));assertTrue(runCatching {TaskRepository(file).load()}.isFailure)
        assertEquals(TaskFrame.MAX_BYTES+1L,file.length());check(file.delete())
        val pending=File(file.path+".new");pending.writeBytes(TaskFrame.encode(TaskBook()))
        assertTrue(runCatching {TaskRepository(file).load()}.isFailure)
        assertTrue(pending.exists());assertFalse(file.exists())
    }
    @Test fun actualExplicitAtomicWriteAbortPreservesConfirmedSnapshot() = fixture {file ->
        val repo=TaskRepository(file);val saved=repo.add(0,"Keep confirmed",null)
        val atomic=AtomicFile(file);val stream=atomic.startWrite();stream.write(byteArrayOf(1,2,3));atomic.failWrite(stream)
        assertEquals(saved.tasks,TaskRepository(file).load().tasks)
        assertEquals(saved.revision,TaskRepository(file).load().revision)
    }
}
