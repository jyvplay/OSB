package dev.fusion.format.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ImageInfo(val width: Int, val height: Int, val mime: String, val bytes: Long)

/**
 * Platform-only image handling.
 *
 * Decoding uses ImageDecoder, so whatever the device supports is supported here
 * (JPEG/PNG/WebP/GIF/HEIF on current Android, AVIF on API 31+). Nothing is
 * bundled, so there is no x265 and no GPL exposure.
 *
 * Export is limited to JPEG / PNG / WebP because those are the only formats
 * Bitmap.compress can write. HEIC *encoding* is NOT available here.
 */
object ImageOps {

    enum class ExportFormat(val mime: String) {
        JPEG("image/jpeg"), PNG("image/png"), WEBP("image/webp")
    }

    /** Existing software bitmap only. Android premultiplication/color conversion is NOT byte fidelity.
     * No decode/export/source mutation is performed. Caller owns/recycles the returned bitmap.
     */
    fun rotate(bitmap: Bitmap, quarterTurns: Int): Bitmap = toBitmap(samples(bitmap).rotateClockwise(quarterTurns))
    fun crop(bitmap: Bitmap, left: Int, top: Int, right: Int, bottom: Int): Bitmap =
        toBitmap(samples(bitmap).crop(left, top, right, bottom))
    fun flip(bitmap: Bitmap, horizontal: Boolean): Bitmap = toBitmap(samples(bitmap).flip(horizontal))
    private fun samples(bitmap: Bitmap): ArgbImage {
        require(!bitmap.isRecycled && bitmap.config != Bitmap.Config.HARDWARE)
        require(bitmap.width.toLong() * bitmap.height <= ArgbImage.MAX_PIXELS)
        val data = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(data, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return ArgbImage(bitmap.width, bitmap.height, data)
    }
    private fun toBitmap(image: ArgbImage): Bitmap =
        Bitmap.createBitmap(image.pixels(), image.width, image.height, Bitmap.Config.ARGB_8888)

    /** Reads dimensions without allocating the full bitmap. */
    suspend fun probe(context: Context, uri: Uri): Result<ImageInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Cannot open image" }
                BitmapFactory.decodeStream(input, null, opts)
            }
            require(opts.outWidth > 0 && opts.outHeight > 0) { "Not a decodable image on this device" }
            val size = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
            ImageInfo(opts.outWidth, opts.outHeight, opts.outMimeType ?: "unknown", size)
        }
    }

    /** Decodes, downscaling so the longest edge is at most [maxEdge] px. */
    suspend fun decode(context: Context, uri: Uri, maxEdge: Int = 2048): Result<Bitmap> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(maxEdge in 1..2048) { "Preview edge must be 1..2048 pixels" }
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val longest = maxOf(info.size.width, info.size.height)
                    if (longest > maxEdge) {
                        val scale = maxEdge.toFloat() / longest
                        decoder.setTargetSize(
                            (info.size.width * scale).toInt().coerceAtLeast(1),
                            (info.size.height * scale).toInt().coerceAtLeast(1)
                        )
                    }
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = false
                }
            }
        }

    /** Writes to an ACTION_CREATE_DOCUMENT destination. Never overwrites a source. */
    suspend fun exportTo(
        context: Context,
        destination: Uri,
        bitmap: Bitmap,
        format: ExportFormat,
        quality: Int = 92
    ): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val codec = when (format) {
                ExportFormat.JPEG -> Bitmap.CompressFormat.JPEG
                ExportFormat.PNG -> Bitmap.CompressFormat.PNG
                ExportFormat.WEBP ->
                    if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY
                    else @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP
            }
            context.contentResolver.openOutputStream(destination, "wt").use { out ->
                requireNotNull(out) { "Cannot write to the chosen file" }
                check(bitmap.compress(codec, quality.coerceIn(1, 100), out)) { "Encoder rejected this bitmap" }
                out.flush()
            }
            context.contentResolver.openAssetFileDescriptor(destination, "r")?.use { it.length } ?: 0L
        }
    }
}
