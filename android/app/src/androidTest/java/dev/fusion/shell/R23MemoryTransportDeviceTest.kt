package dev.fusion.shell

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.*
import android.system.OsConstants
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import dev.fusion.format.pdf.sandbox.FormatLease
import dev.fusion.format.pdf.sandbox.FormatWorkerService
import dev.fusion.ooxml.CsvTable
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Actual isolated Binder/SharedMemory/FileObserver, no fake worker, provider or filesystem model. */
class R23MemoryTransportDeviceTest {
    @get:Rule val disposable = DisposableEmulatorRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun assertMemory(result: FormatLease.Evidence) {
        assertEquals("READ_ONLY_SHARED_MEMORY", result.inputTransport)
        assertNotEquals(Process.myUid(), result.uid); assertNotEquals(Process.myPid(), result.pid)
        assertFalse(result.internetGranted); assertEquals(-1L, result.sourceDevice); assertEquals(-1L, result.sourceInode)
    }

    @Test fun memoryCallsRunInDistinctUidAndLiveObserverSeesNoNamedOwnedInput() {
        val events = java.util.Collections.synchronizedList(mutableListOf<String>())
        val started = CountDownLatch(1); val drained = CountDownLatch(1)
        val prefix = "r23-memory-control-${UUID.randomUUID()}"
        val observer = object : FileObserver(context.cacheDir, FileObserver.CREATE or FileObserver.MOVED_TO or FileObserver.CLOSE_WRITE) {
            override fun onEvent(event: Int, path: String?) {
                if (path == "$prefix-before") started.countDown()
                if (path == "$prefix-after") drained.countDown()
                if (path != null && path != "$prefix-before" && path != "$prefix-after") events.add(path)
            }
        }
        val before = File(context.cacheDir, "$prefix-before"); val after = File(context.cacheDir, "$prefix-after")
        observer.startWatching()
        try {
            assertTrue(before.createNewFile()); assertTrue("FileObserver positive control missing", started.await(3, TimeUnit.SECONDS))
            val arithmetic = FormatLease(context).use { it.evaluateMemorySheet(mapOf("A1" to "2", "B1" to "=A1*3")) }
            assertMemory(arithmetic); assertEquals(mapOf("A1" to "2", "B1" to "6"), arithmetic.sheet)
            val empty = FormatLease(context).use { it.evaluateMemoryCsv(byteArrayOf()) }
            assertMemory(empty); assertEquals(0, empty.bytes); assertTrue(empty.table!!.rows.isEmpty())
            val csv = FormatLease(context).use { it.evaluateMemoryCsv("name,value\r\n\"a,b\",=1+1".toByteArray()) }
            assertMemory(csv); assertEquals(listOf("a,b", "=1+1"), csv.table!!.rows[1])
            val shared = InstrumentationRegistry.getInstrumentation().context.assets.open("r22-shared.xlsx").use { it.readBytes() }
            val imported = FormatLease(context).use { it.evaluateMemoryXlsx(shared) }
            assertMemory(imported); assertEquals("shared α 29", imported.table!!.rows[2][2])
            val rows = List(256) { List(32) { "" } }; val dense = CsvTable.of(rows).workbook().bytes
            repeat(2) {
                val output = FormatLease(context).use { it.evaluateMemoryXlsx(dense) }
                assertMemory(output); assertEquals(rows, output.table!!.rows)
            }
            // Positive event after the workload avoids treating a dead/unstarted observer as no-create evidence.
            assertTrue(after.createNewFile()); assertTrue("FileObserver final control missing", drained.await(3, TimeUnit.SECONDS))
            assertEquals("Unexpected intermediate create/unlink: $events", emptyList<String>(), synchronized(events) { events.toList() })
        } finally { observer.stopWatching(); before.delete(); after.delete() }
    }

    @Test fun exactCsvCapMalformedAndOversizeRefuseThenFreshMemoryLeaseSucceeds() {
        val exact = (List(15) { "x".repeat(4096) } + "x".repeat(4081)).joinToString(",").toByteArray()
        assertEquals(65536, exact.size)
        val good = FormatLease(context).use { it.evaluateMemoryCsv(exact) }
        assertMemory(good); assertEquals(4081, good.table!!.rows[0][15].length)
        for (bytes in listOf("\"unclosed".toByteArray(), byteArrayOf(0xff.toByte()), ByteArray(65537))) {
            assertTrue(runCatching { FormatLease(context).use { it.evaluateMemoryCsv(bytes) } }.isFailure)
        }
        val valid = CsvTable.of(listOf(listOf("literal", "=1+1"))).workbook().bytes
        val corrupt = valid.copyOf(); corrupt[14] = (corrupt[14].toInt() xor 1).toByte()
        for (bytes in listOf(corrupt, byteArrayOf(1, 2, 3), ByteArray(2097153))) {
            assertTrue(runCatching { FormatLease(context).use { it.evaluateMemoryXlsx(bytes) } }.isFailure)
        }
        val lease = FormatLease(context)
        lease.use {
            val recovered = it.evaluateMemoryXlsx(valid); assertMemory(recovered)
            assertEquals(listOf(listOf("literal", "=1+1")), recovered.table!!.rows)
            assertTrue(runCatching { it.evaluateMemoryCsv(exact) }.isFailure)
        }
    }

    private data class WireResult(val data: Bundle, val sendingUid: Int)
    /** Independent actual Binder caller, not reflection into the production broker or a fake service. */
    private fun wire(memory: SharedMemory?, bytes: Int, operation: Int = FormatWorkerService.CSV_TABLE, file: ParcelFileDescriptor? = null): WireResult {
        val thread = HandlerThread("r23-wire-reply").apply { start() }
        val connected = CompletableFuture<Messenger>(); val answer = CompletableFuture<WireResult>()
        val id = UUID.randomUUID().toString()
        val reply = Messenger(object : Handler(thread.looper) {
            override fun handleMessage(message: Message) {
                if (message.what == operation && message.data.getString("id") == id)
                    answer.complete(WireResult(Bundle(message.data), message.sendingUid))
            }
        })
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) { connected.complete(Messenger(binder)) }
            override fun onServiceDisconnected(name: ComponentName) { answer.completeExceptionally(IllegalStateException("Disconnected")) }
            override fun onNullBinding(name: ComponentName) { connected.completeExceptionally(IllegalStateException("Null binding")) }
        }
        var bound = false
        try {
            bound = context.bindIsolatedService(Intent(context, FormatWorkerService::class.java), Context.BIND_AUTO_CREATE,
                "r23wire" + id.replace("-", ""), context.mainExecutor, connection)
            check(bound)
            connected.get(12, TimeUnit.SECONDS).send(Message.obtain(null, operation).apply {
                data = Bundle().apply { putString("id", id); putParcelable("memory", memory); putInt("memoryBytes", bytes); putParcelable("input", file) }
                replyTo = reply
            })
            return answer.get(8, TimeUnit.SECONDS).also {
                assertNotEquals(Process.myUid(), it.sendingUid); assertEquals(it.sendingUid, it.data.getInt("uid"))
            }
        } finally { if (bound) context.unbindService(connection); thread.quitSafely() }
    }
    private fun region(bytes: ByteArray, protect: Boolean): SharedMemory {
        val memory = SharedMemory.create("fusion-public-r23-wire", maxOf(1, bytes.size))
        try {
            val map = memory.mapReadWrite()
            try { if (bytes.isEmpty()) map.put(0, 0.toByte()) else map.put(bytes) } finally { SharedMemory.unmap(map) }
            if (protect) assertTrue(memory.setProtect(OsConstants.PROT_READ))
            return memory
        } catch (error: Throwable) { memory.close(); throw error }
    }

    @Test fun realWireRefusesWritableMismatchedAmbiguousUnsupportedAndEmptyPaddingInputs() {
        region("a,b".toByteArray(), true).use { memory ->
            val control = wire(memory, 3); assertTrue(control.data.getBoolean("ok"))
            assertEquals("READ_ONLY_SHARED_MEMORY", control.data.getString("transport"))
            assertEquals(listOf(listOf("a", "b")), CsvTable.readFrame(requireNotNull(control.data.getByteArray("tableResult"))).rows)
            for (length in listOf(-1, 2, 4, Int.MAX_VALUE)) assertFalse(wire(memory, length).data.getBoolean("ok"))
            assertFalse(wire(memory, 3, FormatWorkerService.DIGEST).data.getBoolean("ok"))
            val file = File.createTempFile("r23-wire-control-", ".csv", context.cacheDir).apply { writeText("a,b") }
            try {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                    assertFalse(wire(memory, 3, file = fd).data.getBoolean("ok"))
                    assertFalse(wire(null, 0, file = fd).data.getBoolean("ok"))
                }
                assertEquals("a,b", file.readText())
            } finally { file.delete() }
        }
        region("a,b".toByteArray(), false).use { assertFalse(wire(it, 3).data.getBoolean("ok")) }
        region(ByteArray(65537), true).use { assertFalse(wire(it, 65537).data.getBoolean("ok")) }
        region(byteArrayOf(7), true).use { assertFalse(wire(it, 0).data.getBoolean("ok")) }
        assertFalse(wire(null, 0).data.getBoolean("ok"))
        region("c,d".toByteArray(), true).use { assertTrue(wire(it, 3).data.getBoolean("ok")) }
    }
}
