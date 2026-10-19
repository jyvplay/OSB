// SPDX-License-Identifier: Apache-2.0
package dev.fusion.format.image.sandbox

import android.app.Service
import android.content.Intent
import android.os.*
import android.system.Os
import android.system.OsConstants
import dev.fusion.format.image.PngMetadata
import java.security.MessageDigest

/** One bounded request in a real isolated UID. No native image decoder, named input file, network or user path. */
class PngWorkerService : Service() {
    private var used=false
    private val messenger=Messenger(object:Handler(Looper.getMainLooper()) {
        override fun handleMessage(message:Message) {
            val request=message.data.apply { classLoader=SharedMemory::class.java.classLoader }
            val token=request.getString("token").orEmpty()
            var input:SharedMemory?=null;var output:SharedMemory?=null
            val response=Bundle().apply {putString("token",token);putInt("uid",Process.myUid());putInt("pid",Process.myPid());putBoolean("isolated",Process.isIsolated())}
            try {
                input=request.getParcelable("input",SharedMemory::class.java)
                require(!used && message.what==1 && token.matches(Regex("[a-f0-9-]{36}"))) {"Invalid/repeated request"}
                used=true
                require(Process.isIsolated()) {"Actual isolated UID required"}
                val size=request.getInt("size",-1)
                require(size in 45..PngMetadata.MAX_BYTES && input!=null && input.size==size) {"Input byte regime refused"}
                val bytes=ByteArray(size)
                val mapped=input.mapReadOnly()
                try {mapped.get(bytes)} finally {SharedMemory.unmap(mapped)}
                require(MessageDigest.isEqual(MessageDigest.getInstance("SHA-256").digest(bytes),request.getByteArray("sha256"))) {"Owned input digest mismatch"}
                // Observe actual kernel authority; this is not merely an INTERNET manifest claim.
                var inetDenied=false
                try {val fd=Os.socket(OsConstants.AF_INET,OsConstants.SOCK_STREAM,0);Os.close(fd)}
                catch(e:android.system.ErrnoException) {inetDenied=e.errno==OsConstants.EACCES || e.errno==OsConstants.EPERM}
                require(inetDenied) {"Unexpected network authority"}
                val cleaned=PngMetadata.clean(bytes);val result=cleaned.bytes()
                output=SharedMemory.create("png-clean-copy",result.size)
                val writable=output.mapReadWrite()
                try {writable.put(result)} finally {SharedMemory.unmap(writable)}
                check(output.setProtect(OsConstants.PROT_READ))
                response.putParcelable("output",output);response.putInt("size",result.size)
                response.putByteArray("sha256",MessageDigest.getInstance("SHA-256").digest(result))
                response.putInt("width",cleaned.report.width);response.putInt("height",cleaned.report.height)
                response.putInt("removed",cleaned.report.removedChunkTypes.size)
                response.putBoolean("inet_denied",inetDenied);response.putBoolean("ok",true)
            } catch(error:Exception) {
                response.clear();response.putString("token",token);response.putBoolean("ok",false)
                response.putString("error","PNG copy refused: "+error.javaClass.simpleName) // No input/metadata contents logged.
            }
            try {message.replyTo?.send(Message.obtain().apply {what=2;data=response})}
            finally {input?.close();output?.close()}
        }
    })
    override fun onBind(intent:Intent?)=messenger.binder
}
