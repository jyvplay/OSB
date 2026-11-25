// SPDX-License-Identifier: Apache-2.0
package dev.fusion.files

/**
 * Bounded filename stem/extension splitter handling dotfiles.
 */
class FilePathExtensionSplitter private constructor() {
    companion object {
        fun split(fileName: String): Pair<String, String> {
            require(fileName.isNotEmpty()) { "empty fileName" }
            val dot = fileName.lastIndexOf('.')
            if (dot <= 0 || dot == fileName.length - 1) return fileName to ""
            return fileName.substring(0, dot) to fileName.substring(dot + 1)
        }
    }
}
