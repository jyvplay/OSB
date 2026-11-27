// SPDX-License-Identifier: Apache-2.0
package dev.fusion.together

/**
 * R146 — collaborative edit-log coalescer (bandwidth/latency optimisation for
 * the offline-first sync path; mechanism-distinct from [RgaTextSequence],
 * which is a character CRDT — this kernel compacts a plain positional edit
 * script before it is sent).
 *
 * An edit script is a list of operations applied **sequentially** to a base
 * document: `i<pos>,<text>` inserts `text` so it begins at `pos` in the
 * document as it exists *at that step*, and `d<pos>,<len>` deletes `len`
 * characters starting at `pos` in the document as it exists at that step.
 *
 * Two merge rules preserve the resulting document exactly:
 *  1. consecutive inserts where the later position equals
 *     `earlier.pos + earlier.text.length` fuse into one insert whose text is
 *     the concatenation (positions after the fuse are unchanged because the
 *     fused insert spans the same interval);
 *  2. consecutive deletes where the later position equals the earlier
 *     position fuse into one delete whose length is the sum (the first
 *     delete shifts the original range, so deleting again at the same live
 *     position continues the original range).
 *
 * The coalescer replays the script against the base document to validate
 * every position (an out-of-range position refuses the whole script), then
 * applies the two rules left-to-right until a fixed point, and returns the
 * canonical script.
 *
 * Declared narrowings: base document <= [MAX_DOC] UTF-16 units; at most
 * [MAX_OPS] operations; insert text <= [MAX_INSERT] units; delete length
 * >= 1; positions >= 0.
 */
object EditLogCoalescer {
    const val SCOPE =
        "Positional insert/delete script validation and fixed-point coalescing with document replay; no CRDT, no network."

    const val MAX_DOC = 4096
    const val MAX_OPS = 512
    const val MAX_INSERT = 256

    sealed interface Op {
        data class Ins(val pos: Int, val text: String) : Op
        data class Del(val pos: Int, val len: Int) : Op
    }

    private val OP_RE = Regex("([id])(\\d+),(.*)", RegexOption.DOT_MATCHES_ALL)

    fun parseScript(spec: String): List<Op> {
        if (spec.isEmpty()) return emptyList()
        val raw = spec.split('|')
        require(raw.size <= MAX_OPS) { "too many ops: $MAX_OPS" }
        return raw.map { token ->
            val m = OP_RE.matchEntire(token)
                ?: throw IllegalArgumentException("bad op token: $token")
            val kind = m.groupValues[1]
            val pos = m.groupValues[2].toIntOrNull()
                ?: throw IllegalArgumentException("op position not an int")
            require(pos >= 0) { "negative position" }
            if (kind == "i") {
                val text = m.groupValues[3]
                require(text.length <= MAX_INSERT) { "insert over $MAX_INSERT units" }
                require('|' !in text) { "insert text may not contain the '|' separator" }
                Op.Ins(pos, text)
            } else {
                val len = m.groupValues[3].toIntOrNull()
                    ?: throw IllegalArgumentException("delete length not an int")
                require(len >= 1) { "delete length must be >= 1" }
                Op.Del(pos, len)
            }
        }
    }

    fun render(ops: List<Op>): String = ops.joinToString("|") { op ->
        when (op) {
            is Op.Ins -> "i${op.pos},${op.text}"
            is Op.Del -> "d${op.pos},${op.len}"
        }
    }

    /** Replays `ops` against `base`, refusing any out-of-range access. */
    fun replay(base: String, ops: List<Op>): String {
        require(base.length <= MAX_DOC) { "base document over $MAX_DOC units" }
        require(ops.size <= MAX_OPS) { "too many operations" }
        var doc = base
        for (op in ops) {
            require(doc.length <= MAX_DOC) { "document grew past $MAX_DOC units" }
            when (op) {
                is Op.Ins -> {
                    require(op.pos in 0..doc.length) { "insert position past end of document" }
                    doc = doc.substring(0, op.pos) + op.text + doc.substring(op.pos)
                }
                is Op.Del -> {
                    require(op.pos in 0..doc.length && op.len >= 1 && op.len <= doc.length - op.pos) { "delete range past end of document" }
                    doc = doc.substring(0, op.pos) + doc.substring(op.pos + op.len)
                }
            }
        }
        require(doc.length <= MAX_DOC) { "document grew past $MAX_DOC units" }
        return doc
    }

    /**
     * Validates against `base`, then fuses at a fixed point. The result is
     * guaranteed to replay to the same document as `ops` (checked here by
     * construction: each fuse is document-preserving; the host probe
     * re-checks both sides against an independent oracle).
     */
    fun coalesce(base: String, opsSpec: String): String = coalesce(base, parseScript(opsSpec))

    fun coalesce(base: String, ops: List<Op>): String {
        require(ops.size <= MAX_OPS) { "too many operations" }
        // The structured entry point enforces the same op bounds as the
        // script parser so a direct caller cannot bypass them.
        for (op in ops) {
            when (op) {
                is Op.Ins -> {
                    require(op.pos >= 0) { "insert position must be >= 0" }
                    require(op.text.length <= MAX_INSERT) { "insert over $MAX_INSERT units" }
                    require('|' !in op.text) { "insert text may not contain '|'" }
                }
                is Op.Del -> {
                    require(op.pos >= 0) { "delete position must be >= 0" }
                    require(op.len >= 1) { "delete length must be >= 1" }
                }
            }
        }
        val resultDoc = replay(base, ops) // validates positions first
        var current = ops
        var changed = true
        var rounds = 0
        while (changed) {
            changed = false
            rounds++
            require(rounds <= MAX_OPS + 1) { "coalescing failed to converge" }
            val next = ArrayList<Op>(current.size)
            var i = 0
            while (i < current.size) {
                val a = current[i]
                val b = current.getOrNull(i + 1)
                if (b != null && a is Op.Ins && b is Op.Ins &&
                    b.pos == a.pos + a.text.length && a.text.length + b.text.length <= MAX_INSERT
                ) {
                    next.add(Op.Ins(a.pos, a.text + b.text))
                    i += 2
                    changed = true
                } else if (b != null && a is Op.Del && b is Op.Del && b.pos == a.pos) {
                    // lengths validated during replay; saturate defensively
                    val len = try {
                        Math.addExact(a.len, b.len)
                    } catch (e: ArithmeticException) {
                        throw IllegalArgumentException("fused delete length overflows", e)
                    }
                    next.add(Op.Del(a.pos, len))
                    i += 2
                    changed = true
                } else {
                    next.add(a)
                    i++
                }
            }
            current = next
        }
        // Document-preservation invariant: the fused script must still
        // replay to exactly the same document within the same bounds.
        val fusedDoc = replay(base, current)
        require(fusedDoc == resultDoc) { "coalescing changed the document" }
        return render(current)
    }
}
