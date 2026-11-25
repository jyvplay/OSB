// SPDX-License-Identifier: Apache-2.0
package dev.fusion.model

/**
 * Bounded pagination cursor `(offset, limit)`.
 */
class ModelPaginationCursor private constructor() {
    companion object {
        fun nextOffset(offset: Int, limit: Int, total: Int): Int? {
            require(offset >= 0 && limit > 0 && total >= 0) { "invalid pagination args" }
            val next = offset + limit
            return if (next < total) next else null
        }
    }
}
