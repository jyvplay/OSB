// SPDX-License-Identifier: Apache-2.0
package dev.fusion.documents

/**
 * Bounded CRLF/CR to LF line-ending normalizer.
 */
class DocumentCrLfNormalizer private constructor() {
    companion object {
        fun normalize(text: String): String =
            text.replace("\r\n", "\n").replace('\r', '\n')
    }
}
