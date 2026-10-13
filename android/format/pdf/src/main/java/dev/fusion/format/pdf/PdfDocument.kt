package dev.fusion.format.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable

/**
 * Real page rendering via the platform PdfRenderer (android.graphics.pdf).
 * View-only: no annotation, no signing, no text extraction, no form filling.
 * Encrypted PDFs are rejected by the platform and surface as a clear error.
 */
class PdfDocument private constructor(
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer
) : Closeable {

    val pageCount: Int get() = renderer.pageCount

    /** Renders one page scaled to [targetWidth] px, preserving aspect ratio. */
    suspend fun renderPage(index: Int, targetWidth: Int): Result<Bitmap> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(index in 0 until renderer.pageCount) { "Page $index is out of range" }
                renderer.openPage(index).use { page ->
                    val width = targetWidth.coerceIn(120, 2400)
                    val height = (width.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    // PdfRenderer composites onto transparency; fill white first.
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            }
        }

    override fun close() {
        runCatching { renderer.close() }
        runCatching { descriptor.close() }
    }

    companion object {
        suspend fun open(context: Context, uri: Uri): Result<PdfDocument> =
            withContext(Dispatchers.IO) {
                runCatching {
                    val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                        ?: error("Provider did not return a readable file descriptor")
                    try {
                        PdfDocument(pfd, PdfRenderer(pfd))
                    } catch (e: Exception) {
                        pfd.close()
                        // SecurityException here usually means the PDF is password protected.
                        throw IllegalStateException("Cannot open PDF: ${e.message}", e)
                    }
                }
            }
    }
}
