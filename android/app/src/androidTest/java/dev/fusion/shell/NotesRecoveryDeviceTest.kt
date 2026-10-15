package dev.fusion.shell

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.fusion.notes.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Real production ViewModel/AtomicFile, fresh owners. Not a process-kill or power-cut test. */
@RunWith(AndroidJUnit4::class)
class NotesRecoveryDeviceTest {
    @get:Rule val disposable=DisposableEmulatorRule()
    private val app get()=ApplicationProvider.getApplicationContext<Application>()
    private fun main(block:()->Unit)=InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    private class Owner(val store:ViewModelStore,val model:NotesViewModel)
    private fun owner():Owner {
        lateinit var owner:Owner
        main { val store=ViewModelStore();owner=Owner(store,ViewModelProvider(store,ViewModelProvider.AndroidViewModelFactory.getInstance(app))[NotesViewModel::class.java]) }
        wait(owner.model) { !it.busy };return owner
    }
    private fun close(owner:Owner) = main { owner.store.clear() }
    private fun wait(vm:NotesViewModel,predicate:(NotesState)->Boolean):NotesState = runBlocking { withTimeout(10_000) { vm.state.first(predicate) } }
    private fun flush(vm:NotesViewModel)=runBlocking { withContext(Dispatchers.Main) { vm.flush() } }
    @Test fun freshOwnerRecoversCheckpointWithoutSavingOrReplayingPendingDelete() {
        val name="recovery_"+UUID.randomUUID().toString().replace("-","")
        val files=NotesStore(app);val saved=files.save(name,"saved bytes",null)
        var first:Owner?=null;var second:Owner?=null
        try {
            first=owner();val vm=first.model
            main { vm.request(NoteAction.Open(name)) };wait(vm) { !it.busy && it.name==name }
            main { vm.edit("unsaved α🙂");vm.request(NoteAction.Delete(name,saved.revision)) }
            assertNotNull(vm.state.value.pending);assertTrue(flush(vm));close(first);first=null
            second=owner();val restored=second.model.state.value
            assertEquals("unsaved α🙂",restored.text);assertEquals(saved.revision,restored.revision)
            assertTrue(restored.dirty);assertNull(restored.pending);assertEquals(saved,files.load(name))
        } finally { first?.let(::close);second?.let(::close);File(app.filesDir,"notes/$name.md").delete() }
    }
    @Test fun recoveredStaleRevisionCannotOverwriteNewerSavedBytes() {
        val name="stale_"+UUID.randomUUID().toString().replace("-","")
        val files=NotesStore(app);val saved=files.save(name,"base",null)
        var first:Owner?=null;var second:Owner?=null
        try {
            first=owner();val vm=first.model
            main { vm.request(NoteAction.Open(name)) };wait(vm) { !it.busy && it.name==name }
            main { vm.edit("draft") };assertTrue(flush(vm));close(first);first=null
            val newer=files.save(name,"newer",saved.revision)
            second=owner();val current=second.model
            main { current.save() };wait(current) { !it.busy && it.status.startsWith("Operation not confirmed") }
            assertEquals("draft",current.state.value.text);assertEquals(newer,files.load(name))
        } finally { first?.let(::close);second?.let(::close);File(app.filesDir,"notes/$name.md").delete() }
    }
    @Test fun distinctRecoveryOwnersCannotBlindlyOverwriteEachOther() {
        val dir=File(app.cacheDir,"recovery-owners-"+UUID.randomUUID())
        try {
            val a=NotesRecoveryStore(dir);val b=NotesRecoveryStore(dir)
            try { a.write(NoteDraft("a","unobserved"));fail("Write without read accepted") } catch(_:IllegalStateException) {}
            assertEquals(NoteDraft(),a.read());assertEquals(NoteDraft(),b.read())
            val first=NoteDraft("a","first confirmed")
            a.write(first)
            try { b.write(NoteDraft("b","stale owner"));fail("Stale owner overwrite accepted") } catch(_:NoteConflict) {}
            assertEquals(first,NotesRecoveryStore(dir).read())
            assertEquals(first,b.read());val second=NoteDraft("b","explicitly reconciled")
            b.write(second)
            try { a.write(first);fail("Old owner replay accepted") } catch(_:NoteConflict) {}
            assertEquals(second,NotesRecoveryStore(dir).read())
        } finally { dir.deleteRecursively() }
    }
    @Test fun deletedEmptyCheckpointDoesNotEqualNeverObservedAbsence() {
        val dir=File(app.cacheDir,"recovery-absence-"+UUID.randomUUID())
        try {
            val store=NotesRecoveryStore(dir);store.read();store.write(NoteDraft())
            assertTrue(File(dir,"notes-recovery-v1.bin").delete())
            try { store.write(NoteDraft("n","not published"));fail("Deleted checkpoint silently replaced") } catch(_:NoteConflict) {}
            assertFalse(File(dir,"notes-recovery-v1.bin").exists())
        } finally { dir.deleteRecursively() }
    }
    @Test fun firstReadyStateIncludesInventoryBeforeAcceptingActions() {
        val name="ready_"+UUID.randomUUID().toString().replace("-","")
        val saved=NotesStore(app).save(name,"startup inventory",null)
        val owner=owner()
        try {
            assertTrue(owner.model.state.value.names.contains(name))
            main { owner.model.request(NoteAction.Open(name)) }
            val state=wait(owner.model) { !it.busy && it.name==name }
            assertEquals(saved.text,state.text)
        } finally { close(owner);File(app.filesDir,"notes/$name.md").delete() }
    }
    @Test fun repeatedCheckpointRequestsUseOneWriterMailbox() {
        val owner=owner()
        try {
            main {
                owner.model.name("bounded_queue");owner.model.edit("retained")
                repeat(1000) { owner.model.checkpointNow() }
                // Main looper has not yielded: the old per-click launch would expose1000 queued children here.
                assertEquals(1,owner.model.viewModelScope.coroutineContext[Job]!!.children.count())
            }
            wait(owner.model) { it.recoveryStatus.startsWith("Local draft checkpoint confirmed") }
            assertEquals("retained",owner.model.state.value.text)
        } finally { close(owner) }
    }
    @Test fun unreadableRecoveryBlocksEditsAndPreservesExactFile() {
        val file=File(app.noBackupFilesDir,"notes-recovery-v1.bin");val bytes=byteArrayOf(9,8,7,6);file.writeBytes(bytes)
        val owner=owner()
        try {
            assertTrue(owner.model.state.value.recoveryBlocked)
            main { owner.model.name("not_allowed");owner.model.edit("no overwrite");owner.model.save() }
            assertFalse(flush(owner.model));assertArrayEquals(bytes,file.readBytes());assertEquals("",owner.model.state.value.text)
        } finally { close(owner) }
    }
}
