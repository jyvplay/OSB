package dev.fusion.format.pdf

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import dev.fusion.format.pdf.sandbox.FormatLease
import dev.fusion.format.pdf.sandbox.PdfPreview
import dev.fusion.format.pdf.sandbox.PdfPreviewLimits
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.Closeable

/** Bounded immutable source snapshot. ALL native PDF parsing/rendering lives in the isolated worker.
 * Parent accepts only validated raw ARGB, not child-controlled image codecs. Not full PDF editing.
 */
class PdfDocument private constructor(private val context:Context,private val source:ByteArray,
                                     private var first:PdfPreview?):Closeable {
    private val lock=Any();private var closed=false
    val pageCount=requireNotNull(first).pages
    suspend fun renderPage(index:Int,targetWidth:Int):Result<Bitmap> = withContext(Dispatchers.IO) {
        runCatching {
            require(targetWidth==PdfPreviewLimits.MAX_SIDE) {"Bounded preview requests exactly320px maximum; no silent full-resolution claim"}
            val (bytes,cached)=synchronized(lock) {check(!closed);require(index in 0 until pageCount);source.copyOf() to first?.takeIf {index==0}}
            val frame=cached ?: FormatLease(context).use {requireNotNull(it.previewOwnedPdf(bytes,index).pdf)}
            require(frame.pages==pageCount) {"Worker page count changed for the same snapshot"}
            synchronized(lock) {check(!closed)}
            Bitmap.createBitmap(frame.pixels(),frame.width,frame.height,Bitmap.Config.ARGB_8888)
        }
    }
    override fun close() {synchronized(lock) {closed=true;source.fill(0);first=null}} // Copies/OS buffers are NOT securely erased.
    companion object {
        suspend fun open(context:Context,uri:Uri):Result<PdfDocument> = withContext(Dispatchers.IO) {
            runCatching {
                val out=ByteArrayOutputStream()
                requireNotNull(context.contentResolver.openInputStream(uri)).use {input->
                    val chunk=ByteArray(8192)
                    while(true) {
                        val n=input.read(chunk,0,minOf(chunk.size,PdfPreviewLimits.MAX_BYTES-out.size()+1))
                        if(n<0) break
                        require(n>0 && out.size()+n<=PdfPreviewLimits.MAX_BYTES) {"PDF source byte limit exceeded"}
                        out.write(chunk,0,n)
                    }
                }
                val bytes=out.toByteArray()
                val evidence=FormatLease(context).use {it.previewOwnedPdf(bytes,0)}
                PdfDocument(context.applicationContext,bytes,requireNotNull(evidence.pdf))
            }
        }
    }
}
