package dev.fusion.format.pdf.sandbox

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.*
import dev.fusion.ooxml.SheetFrame
import dev.fusion.ooxml.OwnedSheetRequest
import java.io.Closeable
import java.io.File
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** One operation/isolated process. No URI, output path, network permission or parser engine transfer. */
class FormatLease(context: Context) : Closeable {
    data class Evidence(val uid: Int,val pid: Int,val bytes: Int,val digest: String,
                        val internetGranted: Boolean,val privateAccessDenied: Boolean,val privateErrno: Int,
                        val privateProbePath: String?,val sourceDevice: Long,val sourceInode: Long,
                        val socketOutcome: String?,val socketErrno: Int,val sheet:Map<String,String>?=null)
    private val context=context.applicationContext.also {
        check(Looper.myLooper()!=Looper.getMainLooper()) { "Open sandbox leases off the UI thread" }
    }
    private val thread=HandlerThread("format-reply").apply { start() }
    private val connected=CompletableFuture<Messenger>()
    private val result=CompletableFuture<Evidence>()
    private val death=CountDownLatch(1)
    private val started=AtomicBoolean(false)
    private val expectedOperation=java.util.concurrent.atomic.AtomicInteger(0)
    private val ownedRequest=java.util.concurrent.atomic.AtomicReference<OwnedSheetRequest?>(null)
    private val closed=AtomicBoolean(false)
    private val id=UUID.randomUUID().toString()
    private var bound=false
    private val reply=Messenger(object: Handler(thread.looper) {
        override fun handleMessage(message: Message) {
            if(closed.get() || message.what!=expectedOperation.get()) return
            val data=message.data
            if(data.getString("id")!=id) return
            val uid=data.getInt("uid",-1)
            if(message.sendingUid!=uid || uid==Process.myUid() || uid<0) {
                result.completeExceptionally(IllegalStateException("Response did not originate from an isolated UID"));return
            }
            if(!data.getBoolean("ok")) {
                result.completeExceptionally(IllegalStateException(data.getString("error")?.take(240) ?: "Worker refused"));return
            }
            val digest=data.getString("digest") ?: ""
            val size=data.getInt("bytes",-1)
            if(!digest.matches(Regex("[0-9a-f]{64}")) || size !in 0..FormatWorkerService.MAX_INPUT_BYTES) {
                result.completeExceptionally(IllegalStateException("Malformed worker result"));return
            }
            val sheet=if(message.what==FormatWorkerService.DECIMAL_SHEET) {
                try {
                    val frame=requireNotNull(data.getByteArray("sheetResult"))
                    require(size<=SheetFrame.MAX_INPUT)
                    ownedRequest.get()?.accept(size,digest,frame) ?: SheetFrame.readResult(frame)
                } catch(error:Exception) { result.completeExceptionally(error);return }
            } else null
            result.complete(Evidence(uid,data.getInt("pid"),size,digest,data.getBoolean("internetGranted"),data.getBoolean("privateAccessDenied"),data.getInt("privateErrno",-1),
                data.getString("privateProbePath"),data.getLong("sourceDevice"),data.getLong("sourceInode"),data.getString("socketOutcome"),data.getInt("socketErrno",-1),sheet))
        }
    })
    private val connection=object: ServiceConnection {
        override fun onServiceConnected(name: ComponentName,binder: IBinder) {
            try {
                binder.linkToDeath({ death.countDown();unavailable("Worker died") },0)
                connected.complete(Messenger(binder))
            } catch(error: Exception) { connected.completeExceptionally(error) }
        }
        override fun onServiceDisconnected(name: ComponentName) { death.countDown();unavailable("Worker disconnected") }
        override fun onBindingDied(name: ComponentName) { death.countDown();unavailable("Binding died") }
        override fun onNullBinding(name: ComponentName) { unavailable("Null binding") }
    }
    init {
        try {
            bound=this.context.bindIsolatedService(Intent(this.context,FormatWorkerService::class.java),Context.BIND_AUTO_CREATE,
                "job"+id.replace("-",""),this.context.mainExecutor,connection)
            check(bound) { "Isolated service unavailable" }
            connected.get(12,TimeUnit.SECONDS)
        } catch(error: Exception) { close();throw error }
    }
    private fun unavailable(reason: String) {
        val error=IllegalStateException(reason)
        connected.completeExceptionally(error);result.completeExceptionally(error)
    }
    /** A fresh open is mandatory: dup alone shares file position with its owner. Caller owns the source. */
    fun digest(file: File, privateProbeName: String?=null): Evidence =
        ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY).use { digestDescriptor(it,privateProbeName) }
    /** Input is an FSC1 frame, not an Office document. Result remains untrusted bounded data. */
    fun evaluateSheet(file:File):Evidence = ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY).use {
        request(it,null,FormatWorkerService.DECIMAL_SHEET,0)
    }
    /** Original bounded caller-owned arithmetic only. Writer is closed before a read-only FD
     * is opened; path is unlinked before sending. No path/URI grant is sent to the worker.
     * Assumes the trusted app process/private directory are not compromised. Not an OS sealed
     * memfd or all-parser sandbox. [U] verify on API 34/36/37 and OEM filesystems.
     */
    fun evaluateOwnedSheet(cells:Map<String,String>):Evidence {
        try {
            check(!closed.get() && !started.get())
            val model=OwnedSheetRequest.create(cells)
            val file=File.createTempFile("fusion-owned-sheet-",".fsc",context.cacheDir)
            try {
                java.io.FileOutputStream(file).use { out ->out.write(model.frame());out.fd.sync() }
                return ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                    check(file.delete()) { "Could not unlink owned request" }
                    request(fd,null,FormatWorkerService.DECIMAL_SHEET,0,model)
                }
            } finally { file.delete() }
        } finally { close() }
    }
    fun probeSocket(file: File,loopbackPort: Int): Evidence {
        require(loopbackPort in 1..65535)
        return ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY).use {
            request(it,null,FormatWorkerService.SOCKET_PROBE,loopbackPort)
        }
    }
    fun digestDescriptor(input: ParcelFileDescriptor,privateProbeName: String?=null): Evidence =
        request(input,privateProbeName,FormatWorkerService.DIGEST,0)
    private fun request(input: ParcelFileDescriptor,privateProbeName: String?,operation: Int,port: Int,owned:OwnedSheetRequest?=null): Evidence {
        check(Looper.myLooper()!=Looper.getMainLooper()) { "Wait for workers off the UI thread" }
        check(!closed.get() && started.compareAndSet(false,true)) { "One request per lease" }
        ownedRequest.set(owned)
        expectedOperation.set(operation)
        try {
            require(privateProbeName==null || privateProbeName.matches(Regex(FormatWorkerService.PRIVATE_NAME)))
            val data=Bundle().apply { putString("id",id);putParcelable("input",input);putString("privateName",privateProbeName);putInt("socketPort",port) }
            connected.get().send(Message.obtain(null,operation).apply { this.data=data;replyTo=reply })
            return result.get(8,TimeUnit.SECONDS)
        } finally { close() }
    }
    /** Observes actual Binder death, not a simulated clock or a guessed process state. */
    fun awaitDeath(timeoutMs: Long): Boolean {
        require(timeoutMs in 1..15000)
        check(Looper.myLooper()!=Looper.getMainLooper())
        return death.await(timeoutMs,TimeUnit.MILLISECONDS)
    }
    override fun close() {
        if(!closed.compareAndSet(false,true)) return
        unavailable("Lease closed")
        if(bound) { runCatching { context.unbindService(connection) };bound=false }
        thread.quitSafely()
    }
}
