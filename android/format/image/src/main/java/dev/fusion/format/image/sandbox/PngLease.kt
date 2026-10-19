// SPDX-License-Identifier: Apache-2.0
package dev.fusion.format.image.sandbox

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.*
import android.system.OsConstants
import dev.fusion.format.image.PngMetadata
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Owned one-shot memory request. Parent performs bounded structural derivative/readback checks, never native image decoding.
 * Correct-worker/trusted-OS assumptions remain. A timeout is not proof of hostile-provider/OS termination or forensic erasure.
 */
class PngLease(private val context:Context) {
    class Result internal constructor(bytes:ByteArray,val width:Int,val height:Int,val removed:Int,val workerUid:Int,val workerPid:Int,val inetDenied:Boolean) {
        private val owned=bytes.copyOf()
        fun bytes():ByteArray=owned.copyOf()
    }
    private data class Reply(val bundle:Bundle,val sender:Int)
    companion object { private val slot=Semaphore(1) }
    fun clean(input:ByteArray):Result {
        check(Looper.myLooper()!=Looper.getMainLooper()) {"PNG request must not block the UI"}
        require(input.size in 45..PngMetadata.MAX_BYTES)
        check(slot.tryAcquire()) {"Another PNG worker request is active"}
        val bytes=input.copyOf();val token=UUID.randomUUID().toString();val done=CountDownLatch(1)
        val answer=AtomicReference<Reply?>();val failure=AtomicReference<String?>();val closed=AtomicBoolean(false)
        var bound=false
        val guard=Any()
        var allocation:SharedMemory?=null
        try {
            allocation=SharedMemory.create("png-owned-input",bytes.size)
            val writable=allocation.mapReadWrite()
            try {writable.put(bytes)} finally {SharedMemory.unmap(writable)}
            check(allocation.setProtect(OsConstants.PROT_READ))
        } catch(error:Throwable) { allocation?.close();slot.release();throw error }
        val owned=requireNotNull(allocation)
        val reply=Messenger(object:Handler(Looper.getMainLooper()) {
            override fun handleMessage(message:Message) {
                val data=message.data.apply {classLoader=SharedMemory::class.java.classLoader}
                synchronized(guard) {
                    if(closed.get() || data.getString("token")!=token || message.what!=2) {
                        data.getParcelable("output",SharedMemory::class.java)?.close();return
                    }
                    if(!answer.compareAndSet(null,Reply(data,message.sendingUid))) data.getParcelable("output",SharedMemory::class.java)?.close()
                    done.countDown()
                }
            }
        })
        val connection=object:ServiceConnection {
            override fun onServiceConnected(name:ComponentName,binder:IBinder) {
                if(closed.get())return
                try {Messenger(binder).send(Message.obtain().apply {what=1;replyTo=reply;data=Bundle().apply {
                    putString("token",token);putParcelable("input",owned);putInt("size",bytes.size)
                    putByteArray("sha256",MessageDigest.getInstance("SHA-256").digest(bytes))
                }})} catch(e:Exception) {failure.set("Worker send failed: "+e.javaClass.simpleName);done.countDown()}
            }
            override fun onServiceDisconnected(name:ComponentName) {failure.set("Worker disconnected");done.countDown()}
            override fun onNullBinding(name:ComponentName) {failure.set("Worker binding unavailable");done.countDown()}
            override fun onBindingDied(name:ComponentName) {failure.set("Worker binding died");done.countDown()}
        }
        try {
            val intent=Intent(context,PngWorkerService::class.java)
            bound=context.bindIsolatedService(intent,Context.BIND_AUTO_CREATE,"png"+token.replace("-",""),context.mainExecutor,connection)
            check(bound && done.await(8,TimeUnit.SECONDS)) {"PNG worker deadline/bind unavailable"}
            check(failure.get()==null) {failure.get().orEmpty()}
            val received=requireNotNull(answer.get()) {"No owned worker response"};val bundle=received.bundle
            check(bundle.getBoolean("ok")) {bundle.getString("error")?:"Worker refused PNG"}
            check(received.sender!=Process.myUid() && received.sender==bundle.getInt("uid") && bundle.getInt("pid")!=Process.myPid() && bundle.getBoolean("isolated")) {"Actual isolated identity mismatch"}
            check(bundle.getBoolean("inet_denied")) {"Worker network boundary unavailable"}
            val size=bundle.getInt("size",-1)
            val out=requireNotNull(bundle.getParcelable("output",SharedMemory::class.java))
            val result:ByteArray
            try {
                require(size in 45..PngMetadata.MAX_BYTES && out.size==size)
                val mapped=out.mapReadOnly()
                try {result=ByteArray(size);mapped.get(result)} finally {SharedMemory.unmap(mapped)}
            } finally {out.close();bundle.remove("output")}
            check(MessageDigest.isEqual(MessageDigest.getInstance("SHA-256").digest(result),bundle.getByteArray("sha256"))) {"Worker output digest mismatch"}
            val proof=requireNotNull(PngMetadata.derivative(bytes,result)) {"Worker output is not the exact retained-chunk derivative"}
            val width=bundle.getInt("width");val height=bundle.getInt("height");val removed=bundle.getInt("removed")
            require(width==proof.width && height==proof.height && removed==proof.removedChunks) {"Worker summary differs from parent structural proof"}
            require(width in 1..PngMetadata.MAX_SIDE && height in 1..PngMetadata.MAX_SIDE && width.toLong()*height<=PngMetadata.MAX_PIXELS && removed in 0..PngMetadata.MAX_CHUNKS)
            return Result(result,width,height,removed,received.sender,bundle.getInt("pid"),true)
        } finally {
            synchronized(guard) {
                closed.set(true)
                answer.get()?.bundle?.getParcelable("output",SharedMemory::class.java)?.close()
            }
            try {owned.close();if(bound) context.unbindService(connection)} finally {slot.release()}
        }
    }
}
