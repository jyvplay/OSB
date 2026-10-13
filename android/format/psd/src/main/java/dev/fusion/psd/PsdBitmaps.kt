package dev.fusion.psd

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

/**
 * Thin Android bridge: turns the pure [PsdRaster] produced by
 * [PsdCompositeEngine] into a `Bitmap`.
 *
 * Kept separate from the decode core so the parser stays unit-testable on a
 * plain JVM (android.graphics.Bitmap is only a stub in local unit tests).
 */
object PsdBitmaps {

    suspend fun decodeBitmap(input: InputStream, maxDimension: Int = 2048): Result<Bitmap> =
        withContext(Dispatchers.IO) {
            PsdCompositeEngine.decodeRaster(input).mapCatching { raster ->
                val full = Bitmap.createBitmap(
                    raster.argb, raster.width, raster.height, Bitmap.Config.ARGB_8888
                )
                val longestEdge = maxOf(raster.width, raster.height)
                if (longestEdge <= maxDimension) {
                    full
                } else {
                    val scale = maxDimension.toFloat() / longestEdge
                    val scaled = Bitmap.createScaledBitmap(
                        full,
                        (raster.width * scale).toInt().coerceAtLeast(1),
                        (raster.height * scale).toInt().coerceAtLeast(1),
                        true
                    )
                    if (scaled !== full) full.recycle()
                    scaled
                }
            }
        }
}
