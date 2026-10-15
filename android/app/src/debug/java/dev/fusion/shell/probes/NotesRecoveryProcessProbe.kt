package dev.fusion.shell.probes

import android.app.Service
import android.content.Intent
import android.os.*
import dev.fusion.notes.NoteDraft
import dev.fusion.notes.NotesRecoveryStore
import java.io.File

/** Dedicated debug process for a real kill/restart experiment. Never owns the live Notes checkpoint. */
class NotesRecoveryProcessProbe:Service() {
    companion object { const val TOKEN="dev.fusion.r12.recovery";const val WRITE=1;const val READ=2;const val STOP=3 }
    private val store by lazy { NotesRecoveryStore(File(noBackupFilesDir,"r12-process-probe").apply { check(mkdirs() || isDirectory) }) }
    private val endpoint=object:Binder() {
        override fun onTransact(code:Int,data:Parcel,reply:Parcel?,flags:Int):Boolean {
            if(code !in WRITE..STOP) return super.onTransact(code,data,reply,flags)
            check(getCallingUid()==Process.myUid());data.enforceInterface(TOKEN)
            val out=requireNotNull(reply)
            try {
                synchronized(this@NotesRecoveryProcessProbe) {
                    if(code==WRITE) {
                        val text=requireNotNull(data.readString());require(text.length<=128)
                        store.write(NoteDraft("process_probe",text))
                    }
                    val recovered=store.read()
                    out.writeNoException();out.writeInt(Process.myPid());out.writeString(recovered.text)
                    if(code==STOP) Handler(Looper.getMainLooper()).postDelayed({ Process.killProcess(Process.myPid()) },100)
                }
            } catch(e:Exception) { out.writeException(e) }
            return true
        }
    }
    override fun onBind(intent:Intent)=endpoint
    override fun onDestroy() { super.onDestroy();Process.killProcess(Process.myPid()) }
}
