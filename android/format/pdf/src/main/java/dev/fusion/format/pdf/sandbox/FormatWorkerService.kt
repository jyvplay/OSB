package dev.fusion.format.pdf.sandbox

import android.Manifest
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.*
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.ByteArrayOutputStream
import dev.fusion.ooxml.SheetFrame
import dev.fusion.ooxml.CsvTable
import dev.fusion.ooxml.LiteralXlsxImport
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** FF-12 spike: real isolated UID + read-only descriptor boundary, bounded original arithmetic experiment; not an Office/PDF engine. */
class FormatWorkerService : Service() {
    companion object {
        const val DIGEST = 1
        const val SOCKET_PROBE = 2
        const val DECIMAL_SHEET = 3
        const val CSV_TABLE = 4
        const val XLSX_TABLE = 5
        const val MAX_INPUT_BYTES = 16 * 1024 * 1024
        const val LIFETIME_MS = 5000L
        internal const val PRIVATE_NAME = "r11-isolation-[a-zA-Z0-9]+\\.tmp"
    }
    private val admitted=AtomicBoolean(false)
    private val work=Executors.newSingleThreadExecutor()
    private val watchdog=Executors.newSingleThreadScheduledExecutor()
    private val endpoint by lazy { Messenger(object: Handler(Looper.getMainLooper()) {
        override fun handleMessage(message: Message) {
            // Manifest binding is non-exported; Messenger stamps the actual sending UID in the framework.
            if(message.sendingUid!=applicationInfo.uid) return
            // Handler recycles Message after return; never capture it in the worker closure.
            val operation=message.what
            val data=message.data
            val input=data.getParcelable("input",ParcelFileDescriptor::class.java)
            val reply=message.replyTo
            val id=data.getString("id")
            val privateName=data.getString("privateName")
            val socketPort=data.getInt("socketPort",0)
            if(operation !in setOf(DIGEST,SOCKET_PROBE,DECIMAL_SHEET,CSV_TABLE,XLSX_TABLE) || reply==null || id==null || id.length!=36 || !admitted.compareAndSet(false,true)) {
                input?.close();return
            }
            work.execute {
                val response=Bundle().apply { putString("id",id);putInt("uid",Process.myUid());putInt("pid",Process.myPid()) }
                try {
                    requireNotNull(input) { "Missing input descriptor" }
                    input.use { fd ->
                        val stat=Os.fstat(fd.fileDescriptor)
                        response.putLong("sourceDevice",stat.st_dev);response.putLong("sourceInode",stat.st_ino)
                        require(OsConstants.S_ISREG(stat.st_mode)) { "Regular file required" }
                        require(Os.lseek(fd.fileDescriptor,0,OsConstants.SEEK_CUR)==0L) { "Fresh descriptor at offset zero required" }
                        require(Os.fcntlInt(fd.fileDescriptor,OsConstants.F_GETFL,0) and OsConstants.O_ACCMODE==OsConstants.O_RDONLY) {
                            "Read-only descriptor required"
                        }
                        val hash=MessageDigest.getInstance("SHA-256");var size=0
                        val limit=when(operation) {DECIMAL_SHEET->SheetFrame.MAX_INPUT;CSV_TABLE->CsvTable.MAX_INPUT;XLSX_TABLE->LiteralXlsxImport.MAX_INPUT;else->MAX_INPUT_BYTES}
                        val payload=if(operation==DECIMAL_SHEET || operation==CSV_TABLE || operation==XLSX_TABLE) ByteArrayOutputStream() else null
                        // A dup shares the offset with its owner. The broker must provide a fresh open, not reuse a cursor.
                        ParcelFileDescriptor.AutoCloseInputStream(ParcelFileDescriptor.dup(fd.fileDescriptor)).use { stream ->
                            val bytes=ByteArray(8192)
                            while(true) {
                                val n=stream.read(bytes,0,minOf(bytes.size,limit-size+1))
                                if(n<0) break
                                require(n>0) { "No progress" };size+=n
                                require(size<=limit) { "Input byte budget exceeded" };hash.update(bytes,0,n);payload?.write(bytes,0,n)
                            }
                        }
                        response.putString("digest",hash.digest().joinToString("") { "%02x".format(it) })
                        response.putInt("bytes",size)
                        // Original bounded parser now runs in this actual isolated worker, never on Binder/main.
                        // [U] verify on API 34/36/37: Binder result bounds, service death and parser behavior.
                        if(operation==DECIMAL_SHEET) response.putByteArray("sheetResult",SheetFrame.execute(requireNotNull(payload).toByteArray()))
                        if(operation==CSV_TABLE) response.putByteArray("tableResult",CsvTable.parse(requireNotNull(payload).toByteArray()).frame())
                        if(operation==XLSX_TABLE) response.putByteArray("tableResult",LiteralXlsxImport.read(requireNotNull(payload).toByteArray()).frame())
                    }
                    if(operation==SOCKET_PROBE) {
                        require(socketPort in 1..65535)
                        // Test-owned IPv4 loopback only; no DNS, external host or arbitrary address accepted.
                        // [U] verify on API 34/36/37: actual isolated socket denial, not just permission metadata.
                        var outcome="CONNECTED"
                        try {
                            val socket=Os.socket(OsConstants.AF_INET,OsConstants.SOCK_STREAM or OsConstants.SOCK_CLOEXEC,0)
                            try {
                                Os.setsockoptTimeval(socket,OsConstants.SOL_SOCKET,OsConstants.SO_SNDTIMEO,android.system.StructTimeval.fromMillis(1000))
                                Os.connect(socket,java.net.InetAddress.getByAddress(byteArrayOf(127,0,0,1)),socketPort)
                            } finally { Os.close(socket) }
                        } catch(error: ErrnoException) {
                            val errno=error.errno
                            outcome=if(errno==OsConstants.EACCES || errno==OsConstants.EPERM) "DENIED" else "OTHER_FAILURE"
                            response.putInt("socketErrno",errno)
                        } catch(_: SecurityException) { outcome="SECURITY_EXCEPTION" }
                        response.putString("socketOutcome",outcome)
                    }
                    response.putBoolean("internetGranted",checkSelfPermission(Manifest.permission.INTERNET)==PackageManager.PERMISSION_GRANTED)
                    if(privateName!=null) {
                        require(privateName.matches(Regex(PRIVATE_NAME)))
                        val candidate=File(File(applicationInfo.dataDir,"files"),privateName)
                        response.putString("privateProbePath",candidate.absolutePath)
                        // Observe the kernel errno directly, not a Java IOException cause-chain convention.
                        // [U] verify on API 34/36/37: isolated-UID app-private path denial errno.
                        var errno=0
                        try { val probe=Os.open(candidate.absolutePath,OsConstants.O_RDONLY or OsConstants.O_CLOEXEC,0);Os.close(probe) }
                        catch(error: ErrnoException) { errno=error.errno }
                        response.putInt("privateErrno",errno)
                        response.putBoolean("privateAccessDenied",errno==OsConstants.EACCES)
                    }
                    response.putBoolean("ok",true)
                } catch(error: Exception) {
                    runCatching { input?.close() }
                    response.putBoolean("ok",false)
                    response.putString("error",error.javaClass.simpleName.take(80)+": "+(error.message ?: "refused").take(160))
                }
                runCatching { reply.send(Message.obtain(null,operation).apply { this.data=response }) }
                // Keep the process alive until the parent unbinds or the fixed lifetime expires.
            }
        }
    }) }
    override fun onCreate() {
        super.onCreate()
        // Independent of the Binder/main/worker loops. Not a defense against arbitrary code execution in this process.
        watchdog.schedule({ Process.killProcess(Process.myPid()) },LIFETIME_MS,TimeUnit.MILLISECONDS)
    }
    override fun onBind(intent: Intent): IBinder = endpoint.binder
    override fun onDestroy() {
        work.shutdownNow();watchdog.shutdownNow()
        super.onDestroy()
        Process.killProcess(Process.myPid()) // This instance's isolated PID only, never caller-controlled.
    }
}
