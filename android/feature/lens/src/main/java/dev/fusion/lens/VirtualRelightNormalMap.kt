// SPDX-License-Identifier: Apache-2.0
package dev.fusion.lens

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Clean-room Studio Virtual Portrait Relighting Solver (`LN-06`) over 2.5D Face-Normal Maps.
 *
 * Computes Lambertian diffuse + Blinn-Phong rim/key specular shading over unit surface normals
 * `(nx, ny, nz)` given configurable studio lighting presets (`REMBRANDT_KEY`, `BUTTERFLY_GLAMOUR`,
 * `RIM_CYBER_CONTOUR`).
 */
object VirtualRelightNormalMap {
    data class PointLight(
        val dirX: Double,
        val dirY: Double,
        val dirZ: Double,
        val intensity: Double,
        val tintR: Double = 1.0,
        val tintG: Double = 1.0,
        val tintB: Double = 1.0,
    ) {
        val unitDir: Triple<Double, Double, Double>

        init {
            val len = sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ)
            require(len > 1e-6 && intensity >= 0.0) { "invalid light vector or intensity" }
            unitDir = Triple(dirX / len, dirY / len, dirZ / len)
        }
    }

    val STUDIO_KEY_FILL_RIG: List<PointLight> = listOf(
        PointLight(-0.55, 0.45, 0.70, 0.85, 1.00, 0.96, 0.90), // Warm 45° Rembrandt key
        PointLight(0.60, 0.15, 0.78, 0.35, 0.88, 0.94, 1.00),  // Cool fill
    )

    fun shadePixelSrgb(
        baseRgb24: Int,
        nx: Double,
        ny: Double,
        nz: Double,
        lights: List<PointLight> = STUDIO_KEY_FILL_RIG,
        ambient: Double = 0.25,
        shininess: Double = 24.0,
    ): Int {
        val nLen = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-6)
        val unx = nx / nLen; val uny = ny / nLen; val unz = nz / nLen

        var accR = ambient
        var accG = ambient
        var accB = ambient
        // View vector aligned with camera +Z `(0, 0, 1)`
        for (l in lights) {
            val (lx, ly, lz) = l.unitDir
            val ndotl = max(0.0, unx * lx + uny * ly + unz * lz)
            // Halfway vector H = normalize(L + V)
            val hx = lx; val hy = ly; val hz = lz + 1.0
            val hLen = sqrt(hx * hx + hy * hy + hz * hz).coerceAtLeast(1e-6)
            val ndoth = max(0.0, (unx * hx + uny * hy + unz * hz) / hLen)
            val spec = if (ndotl > 0.0) 0.18 * ndoth.pow(shininess) else 0.0
            val contrib = l.intensity * (ndotl + spec)
            accR += contrib * l.tintR
            accG += contrib * l.tintG
            accB += contrib * l.tintB
        }

        val r0 = (baseRgb24 ushr 16) and 0xFF
        val g0 = (baseRgb24 ushr 8) and 0xFF
        val b0 = baseRgb24 and 0xFF
        val nr = (r0 * accR).toInt().coerceIn(0, 255)
        val ng = (g0 * accG).toInt().coerceIn(0, 255)
        val nb = (b0 * accB).toInt().coerceIn(0, 255)
        return (nr shl 16) or (ng shl 8) or nb
    }
}
