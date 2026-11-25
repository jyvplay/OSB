// SPDX-License-Identifier: Apache-2.0
package dev.fusion.index

import kotlin.math.sqrt

/**
 * Production-grade TF-IDF cosine similarity & sorted posting-list intersector.
 */
class IndexCosineSimilarity private constructor() {
    companion object {
        fun cosineSimilarity(a: Map<String, Double>, b: Map<String, Double>): Double {
            if (a.isEmpty() || b.isEmpty()) return 0.0
            var dot = 0.0
            var normA = 0.0
            var normB = 0.0
            for (v in a.values) normA += v * v
            for (v in b.values) normB += v * v
            if (normA == 0.0 || normB == 0.0) return 0.0
            for ((k, va) in a) {
                val vb = b[k]
                if (vb != null) dot += va * vb
            }
            return dot / (sqrt(normA) * sqrt(normB))
        }

        fun intersectSortedPostings(a: List<Int>, b: List<Int>): List<Int> {
            val out = mutableListOf<Int>()
            var i = 0
            var j = 0
            while (i < a.size && j < b.size) {
                when {
                    a[i] == b[j] -> {
                        out.add(a[i]); i++; j++
                    }
                    a[i] < b[j] -> i++
                    else -> j++
                }
            }
            return out
        }
    }
}
