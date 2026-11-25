// SPDX-License-Identifier: Apache-2.0
package dev.fusion.messages

/**
 * Bounded message draft length and non-blank guard.
 */
class MessagesDraftLengthGuard private constructor() {
    companion object {
        const val MAX_LEN = 4096

        fun isSendable(draft: String): Boolean =
            draft.length <= MAX_LEN && draft.trim().isNotEmpty()
    }
}
