package dev.fusion.format.pdf.sandbox

import java.security.MessageDigest

object PdfPreviewLimits {
    const val MAX_BYTES=2*1024*1024
    const val MAX_PAGES=1024
    const val MAX_SIDE=320
    const val MAX_POINTS=14400
    fun dimensions(width:Int,height:Int):Pair<Int,Int> {
        require(width in 1..MAX_POINTS && height in 1..MAX_POINTS)
        val scale=minOf(MAX_SIDE.toDouble()/width,MAX_SIDE.toDouble()/height,1.0)
        return maxOf(1,(width*scale).toInt()) to maxOf(1,(height*scale).toInt())
    }
}
/** Raw data only; parent must not decode an untrusted child-controlled compressed image. */
class PdfPreview internal constructor(val pages:Int,val index:Int,val width:Int,val height:Int,pixels:IntArray) {
    private val owned=pixels.copyOf()
    fun pixels():IntArray=owned.copyOf()
}
class OwnedPdfRequest(bytes:ByteArray,val index:Int) {
    init {require(bytes.size in 1..PdfPreviewLimits.MAX_BYTES);require(index in 0 until PdfPreviewLimits.MAX_PAGES)}
    private val source=bytes.copyOf()
    val size=source.size
    val digest=MessageDigest.getInstance("SHA-256").digest(source).joinToString("") {"%02x".format(it)}
    fun bytes():ByteArray=source.copyOf()
    fun accept(actualSize:Int,actualDigest:String,pages:Int,page:Int,width:Int,height:Int,pixels:IntArray):PdfPreview {
        require(actualSize==size && actualDigest==digest) {"PDF source correlation mismatch"}
        require(pages in 1..PdfPreviewLimits.MAX_PAGES && page==index && page in 0 until pages) {"PDF page metadata refused"}
        require(width in 1..PdfPreviewLimits.MAX_SIDE && height in 1..PdfPreviewLimits.MAX_SIDE)
        require(pixels.size.toLong()==width.toLong()*height) {"PDF raw pixel budget mismatch"}
        // Declared opaque-white-page preview, not alpha-preserving/full-fidelity PDF interchange.
        val opaque=pixels.copyOf();for(i in opaque.indices) opaque[i]=opaque[i] or (0xff shl 24)
        return PdfPreview(pages,page,width,height,opaque)
    }
}
