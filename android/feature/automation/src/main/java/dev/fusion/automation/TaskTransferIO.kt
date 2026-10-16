package dev.fusion.automation

import android.content.ContentResolver
import android.net.Uri
import java.security.MessageDigest

/** System-picker content grants only. No persisted grants, path conversion, source retirement or overwrite route. */
object TaskTransferIO {
    data class Receipt(val revision:Long,val bytes:Int,val sha256:String)
    fun read(resolver:ContentResolver,uri:Uri):TaskBook {
        require(uri.scheme=="content")
        return requireNotNull(resolver.openInputStream(uri)) {"Input unavailable"}.use {TaskStreams.read(it)}
    }
    /** Caller MUST supply a newly created document (CreateDocument result), never an existing target.
     * A provider may leave partial data after failure; do not assume rollback or physical durability.
     * [U] verify on API 34/36/37: actual provider grants, cancellation and reopen behavior.
     */
    fun writeCreatedDocument(resolver:ContentResolver,created:Uri,book:TaskBook):Receipt {
        return write(resolver,created,book.revision,TaskFrame.encode(book),TaskFrame.MAX_BYTES)
    }
    fun writeCreatedWorkbook(resolver:ContentResolver,created:Uri,book:TaskBook):Receipt =
        write(resolver,created,book.revision,TaskWorkbook.export(book).bytes,512*1024)
    private fun write(resolver:ContentResolver,created:Uri,revision:Long,bytes:ByteArray,limit:Int):Receipt {
        require(created.scheme=="content" && bytes.size<=limit)
        requireNotNull(resolver.openOutputStream(created,"wt")) {"Output unavailable"}.use {it.write(bytes)}
        val observed=requireNotNull(resolver.openInputStream(created)) {"Readback unavailable"}.use {TaskStreams.bytes(it,limit)}
        check(bytes.contentEquals(observed)) {"Export readback differs; created document may be incomplete"}
        return Receipt(revision,bytes.size,MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {"%02x".format(it)})
    }
}
