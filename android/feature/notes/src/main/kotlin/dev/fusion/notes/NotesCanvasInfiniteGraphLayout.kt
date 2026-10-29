// SPDX-License-Identifier: Apache-2.0
package dev.fusion.notes

import kotlin.math.max
import kotlin.math.sqrt

/**
 * Fruchterman–Reingold force-directed layout + non-overlapping card repulsion solver for Fusion
 * Notes Infinite Knowledge Canvas (`NotesScreen` backlink & Zettelkasten card maps).
 */
object NotesCanvasInfiniteGraphLayout {
    const val SCOPE =
        "Deterministic Fruchterman-Reingold force-directed Notes canvas card layout with AABB overlap resolution."

    data class CardNode(val id: String, val widthDp: Double = 180.0, val heightDp: Double = 96.0)
    data class PositionedCard(val id: String, val centerX: Double, val centerY: Double, val widthDp: Double, val heightDp: Double)

    fun layoutCards(
        nodes: List<CardNode>,
        edges: List<Pair<String, String>>,
        canvasWidthDp: Double = 1600.0,
        canvasHeightDp: Double = 1000.0,
        iterations: Int = 60,
    ): List<PositionedCard> {
        if (nodes.isEmpty()) return emptyList()
        val n = nodes.size
        val xs = DoubleArray(n) { i -> canvasWidthDp * 0.5 + 220.0 * kotlin.math.cos(2.0 * Math.PI * i / n) }
        val ys = DoubleArray(n) { i -> canvasHeightDp * 0.5 + 220.0 * kotlin.math.sin(2.0 * Math.PI * i / n) }
        val idToIdx = nodes.withIndex().associate { it.value.id to it.index }
        val kIdeal = sqrt((canvasWidthDp * canvasHeightDp) / max(1, n).toDouble()) * 0.55

        for (step in 0 until iterations) {
            val dx = DoubleArray(n)
            val dy = DoubleArray(n)
            val temp = canvasWidthDp * 0.18 * (1.0 - step.toDouble() / iterations.toDouble()).coerceAtLeast(0.05)

            for (i in 0 until n) {
                for (j in i + 1 until n) {
                    val vx = xs[i] - xs[j]
                    val vy = ys[i] - ys[j]
                    val dist = kotlin.math.hypot(vx, vy).coerceAtLeast(1.0)
                    val rep = (kIdeal * kIdeal) / dist
                    val fx = (vx / dist) * rep
                    val fy = (vy / dist) * rep
                    dx[i] += fx; dy[i] += fy
                    dx[j] -= fx; dy[j] -= fy
                }
            }
            for ((uId, vId) in edges) {
                val u = idToIdx[uId] ?: continue
                val v = idToIdx[vId] ?: continue
                if (u == v) continue
                val vx = xs[u] - xs[v]
                val vy = ys[u] - ys[v]
                val dist = kotlin.math.hypot(vx, vy).coerceAtLeast(1.0)
                val att = (dist * dist) / kIdeal
                val fx = (vx / dist) * att
                val fy = (vy / dist) * att
                dx[u] -= fx; dy[u] -= fy
                dx[v] += fx; dy[v] += fy
            }
            for (i in 0 until n) {
                val disp = kotlin.math.hypot(dx[i], dy[i]).coerceAtLeast(1e-6)
                val stepLen = minOf(disp, temp)
                xs[i] = (xs[i] + (dx[i] / disp) * stepLen).coerceIn(nodes[i].widthDp / 2.0, canvasWidthDp - nodes[i].widthDp / 2.0)
                ys[i] = (ys[i] + (dy[i] / disp) * stepLen).coerceIn(nodes[i].heightDp / 2.0, canvasHeightDp - nodes[i].heightDp / 2.0)
            }
        }

        // Final deterministic AABB separation pass so zero cards overlap
        repeat(24) {
            for (i in 0 until n) {
                for (j in i + 1 until n) {
                    val minSepX = (nodes[i].widthDp + nodes[j].widthDp) * 0.5 + 12.0
                    val minSepY = (nodes[i].heightDp + nodes[j].heightDp) * 0.5 + 12.0
                    val ox = minSepX - kotlin.math.abs(xs[i] - xs[j])
                    val oy = minSepY - kotlin.math.abs(ys[i] - ys[j])
                    if (ox > 0.0 && oy > 0.0) {
                        if (ox < oy) {
                            val dir = if (xs[i] >= xs[j]) 1.0 else -1.0
                            xs[i] += dir * ox * 0.52; xs[j] -= dir * ox * 0.52
                        } else {
                            val dir = if (ys[i] >= ys[j]) 1.0 else -1.0
                            ys[i] += dir * oy * 0.52; ys[j] -= dir * oy * 0.52
                        }
                    }
                }
            }
        }
        return nodes.mapIndexed { i, nd -> PositionedCard(nd.id, xs[i], ys[i], nd.widthDp, nd.heightDp) }
    }
}
