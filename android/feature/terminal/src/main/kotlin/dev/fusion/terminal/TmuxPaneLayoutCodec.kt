// SPDX-License-Identifier: Apache-2.0
package dev.fusion.terminal

/**
 * Clean-room **`tmux` Pane Tree Layout Serializer, Parser & Exact 16-Bit Checksum Calculator**
 * (`tmux` `layout-custom.c` `layout_checksum`: `csum = (csum >> 1) + ((csum & 1) << 15) + ch`).
 *
 * Encodes & validates `cccc,WxH,X,Y{child1,child2}` (horizontal split) and
 * `cccc,WxH,X,Y[child1,child2]` (vertical split) layout strings!
 */
object TmuxPaneLayoutCodec {
    const val SCOPE =
        "Clean-room tmux custom layout tree serializer, parser, geometry sum validator, and exact 16-bit rotate-right checksum codec."

    sealed interface PaneNode {
        val width: Int
        val height: Int
        val x: Int
        val y: Int

        data class Leaf(
            override val width: Int,
            override val height: Int,
            override val x: Int,
            override val y: Int,
            val paneId: Int,
        ) : PaneNode

        data class HorizontalSplit(
            override val width: Int,
            override val height: Int,
            override val x: Int,
            override val y: Int,
            val children: List<PaneNode>,
        ) : PaneNode

        data class VerticalSplit(
            override val width: Int,
            override val height: Int,
            override val x: Int,
            override val y: Int,
            val children: List<PaneNode>,
        ) : PaneNode
    }

    /** Exact `tmux` 16-bit BSD rotate-right checksum over the layout body (after `"cccc,"`). */
    fun computeChecksumHex(layoutBody: String): String {
        var csum = 0
        for (ch in layoutBody) {
            csum = ((csum ushr 1) + ((csum and 1) shl 15) + (ch.code and 0xFF)) and 0xFFFF
        }
        return "%04x".format(csum)
    }

    fun encode(root: PaneNode): String {
        val body = formatNode(root)
        return "${computeChecksumHex(body)},$body"
    }

    fun decode(layoutString: String): PaneNode {
        val comma = layoutString.indexOf(',')
        require(comma == 4) { "tmux layout must begin with 4-hex-digit checksum + ','" }
        val declared = layoutString.substring(0, 4).lowercase()
        val body = layoutString.substring(5)
        val actual = computeChecksumHex(body)
        require(declared == actual) { "tmux layout checksum mismatch: declared=$declared actual=$actual" }
        val (node, endPos) = parseNode(body, 0)
        require(endPos == body.length) { "trailing characters in tmux layout at $endPos" }
        return node
    }

    private fun formatNode(n: PaneNode): String = when (n) {
        is PaneNode.Leaf -> "${n.width}x${n.height},${n.x},${n.y},${n.paneId}"
        is PaneNode.HorizontalSplit -> "${n.width}x${n.height},${n.x},${n.y}{${n.children.joinToString(",") { formatNode(it) }}}"
        is PaneNode.VerticalSplit -> "${n.width}x${n.height},${n.x},${n.y}[${n.children.joinToString(",") { formatNode(it) }}]"
    }

    private fun parseNode(s: String, start: Int): Pair<PaneNode, Int> {
        val headerMatch = Regex("^(\\d+)x(\\d+),(\\d+),(\\d+)").find(s.substring(start))
            ?: throw IllegalArgumentException("malformed tmux pane geometry at $start")
        val w = headerMatch.groupValues[1].toInt()
        val h = headerMatch.groupValues[2].toInt()
        val x = headerMatch.groupValues[3].toInt()
        val y = headerMatch.groupValues[4].toInt()
        var p = start + headerMatch.value.length
        require(p < s.length) { "truncated tmux node after geometry" }
        return when (s[p]) {
            ',' -> {
                p++
                val idMatch = Regex("^(\\d+)").find(s.substring(p))
                    ?: throw IllegalArgumentException("missing tmux leaf paneId at $p")
                val id = idMatch.groupValues[1].toInt()
                PaneNode.Leaf(w, h, x, y, id) to (p + idMatch.value.length)
            }
            '{', '[' -> {
                val open = s[p++]
                val close = if (open == '{') '}' else ']'
                val kids = ArrayList<PaneNode>()
                while (true) {
                    val (child, nextP) = parseNode(s, p)
                    kids.add(child)
                    p = nextP
                    require(p < s.length) { "unclosed '$open' in tmux layout" }
                    if (s[p] == ',') {
                        p++
                    } else if (s[p] == close) {
                        p++
                        break
                    } else {
                        throw IllegalArgumentException("unexpected '${s[p]}' at $p in tmux split")
                    }
                }
                require(kids.size >= 2) { "tmux split requires >= 2 children" }
                val node = if (open == '{') PaneNode.HorizontalSplit(w, h, x, y, kids)
                else PaneNode.VerticalSplit(w, h, x, y, kids)
                node to p
            }
            else -> throw IllegalArgumentException("unexpected '${s[p]}' after geometry at $p")
        }
    }
}
