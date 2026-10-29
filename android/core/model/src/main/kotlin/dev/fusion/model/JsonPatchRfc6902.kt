// SPDX-License-Identifier: Apache-2.0
package dev.fusion.model

/**
 * Clean-room RFC 6901 JSON Pointer + RFC 6902 transactional JSON Patch engine (`JsonPatchRfc6902`).
 *
 * Powers `:core:model` local-first state mutations, undo/redo journals, and deterministic test vectors:
 * - RFC 6901 JSON Pointer resolution (`""` root, `/a/b/0`, `~1` -> `/`, `~0` -> `~`, `-` array tail).
 * - RFC 6902 atomic patch sequence (`add`, `remove`, `replace`, `move`, `copy`, `test`):
 *   if ANY operation or `test` assertion fails, the original `JsonVal` remains untouched (pure functional!).
 * - Includes clean-room recursive-descent JSON parser & canonical key-sorted serializer (`parseJson`, `toJson`).
 */
object JsonPatchRfc6902 {
    const val SCOPE =
        "RFC 6901 JSON Pointer + RFC 6902 atomic JSON Patch (add, remove, replace, move, copy, test) + pure-JVM JSON AST parser/serializer; JSON Merge Patch RFC 7396 out of scope."

    sealed interface JsonVal {
        data object JNull : JsonVal
        data class JBool(val v: Boolean) : JsonVal
        data class JNum(val v: Double) : JsonVal
        data class JStr(val v: String) : JsonVal
        data class JArr(val items: List<JsonVal>) : JsonVal
        data class JObj(val fields: Map<String, JsonVal>) : JsonVal
    }

    sealed interface PatchOp {
        data class Add(val path: String, val value: JsonVal) : PatchOp
        data class Remove(val path: String) : PatchOp
        data class Replace(val path: String, val value: JsonVal) : PatchOp
        data class Move(val from: String, val path: String) : PatchOp
        data class Copy(val from: String, val path: String) : PatchOp
        data class Test(val path: String, val value: JsonVal) : PatchOp
    }

    class PatchException(message: String) : IllegalArgumentException(message)

    fun parsePointer(pointer: String): List<String> {
        if (pointer.isEmpty()) return emptyList()
        if (!pointer.startsWith("/")) throw PatchException("Invalid RFC 6901 pointer (must start with '/'): $pointer")
        return pointer.substring(1).split('/').map { token ->
            // RFC 6901 §4: decode ~1 BEFORE ~0 so "~01" becomes "~1", not "/"!
            var i = 0
            val sb = StringBuilder(token.length)
            while (i < token.length) {
                if (token[i] == '~') {
                    if (i + 1 >= token.length) throw PatchException("Dangling '~' in JSON Pointer: $pointer")
                    when (token[i + 1]) {
                        '0' -> sb.append('~')
                        '1' -> sb.append('/')
                        else -> throw PatchException("Invalid '~${token[i + 1]}' escape in JSON Pointer: $pointer")
                    }
                    i += 2
                } else {
                    sb.append(token[i++])
                }
            }
            sb.toString()
        }
    }

    fun resolve(doc: JsonVal, pointer: String): JsonVal {
        var cur = doc
        for (tok in parsePointer(pointer)) {
            cur = when (cur) {
                is JsonVal.JObj -> cur.fields[tok] ?: throw PatchException("Missing object key '$tok' at $pointer")
                is JsonVal.JArr -> {
                    val idx = parseArrayIndex(tok, cur.items.size, allowEnd = false)
                    cur.items[idx]
                }
                else -> throw PatchException("Cannot traverse scalar at '$tok' in $pointer")
            }
        }
        return cur
    }

    fun apply(doc: JsonVal, ops: List<PatchOp>): JsonVal {
        var cur = doc
        for (op in ops) {
            cur = applySingle(cur, op)
        }
        return cur
    }

    private fun applySingle(doc: JsonVal, op: PatchOp): JsonVal = when (op) {
        is PatchOp.Add -> putAt(doc, parsePointer(op.path), op.value, mode = PutMode.ADD)
        is PatchOp.Remove -> removeAt(doc, parsePointer(op.path))
        is PatchOp.Replace -> putAt(doc, parsePointer(op.path), op.value, mode = PutMode.REPLACE)
        is PatchOp.Move -> {
            if (op.from == op.path) doc
            else {
                if (op.path.startsWith("${op.from}/") && op.from.isNotEmpty()) {
                    throw PatchException("RFC 6902 §4.4: cannot move '${op.from}' into proper child '${op.path}'")
                }
                val v = resolve(doc, op.from)
                val removed = removeAt(doc, parsePointer(op.from))
                putAt(removed, parsePointer(op.path), v, mode = PutMode.ADD)
            }
        }
        is PatchOp.Copy -> {
            val v = resolve(doc, op.from)
            putAt(doc, parsePointer(op.path), v, mode = PutMode.ADD)
        }
        is PatchOp.Test -> {
            val actual = resolve(doc, op.path)
            if (!jsonEquals(actual, op.value)) {
                throw PatchException("RFC 6902 test failed at '${op.path}': expected ${toJson(op.value)}, got ${toJson(actual)}")
            }
            doc
        }
    }

    private enum class PutMode { ADD, REPLACE }

    private fun putAt(node: JsonVal, tokens: List<String>, value: JsonVal, mode: PutMode): JsonVal {
        if (tokens.isEmpty()) return value
        val head = tokens.first()
        val tail = tokens.drop(1)
        return when (node) {
            is JsonVal.JObj -> {
                if (tail.isEmpty()) {
                    if (mode == PutMode.REPLACE && !node.fields.containsKey(head)) {
                        throw PatchException("Cannot replace nonexistent key '$head'")
                    }
                    val copy = LinkedHashMap(node.fields)
                    copy[head] = value
                    JsonVal.JObj(copy)
                } else {
                    val child = node.fields[head] ?: throw PatchException("Missing parent key '$head'")
                    val copy = LinkedHashMap(node.fields)
                    copy[head] = putAt(child, tail, value, mode)
                    JsonVal.JObj(copy)
                }
            }
            is JsonVal.JArr -> {
                if (tail.isEmpty()) {
                    val copy = ArrayList(node.items)
                    if (mode == PutMode.ADD && head == "-") {
                        copy.add(value)
                    } else if (mode == PutMode.ADD) {
                        val idx = parseArrayIndex(head, copy.size, allowEnd = true)
                        copy.add(idx, value)
                    } else {
                        val idx = parseArrayIndex(head, copy.size, allowEnd = false)
                        copy[idx] = value
                    }
                    JsonVal.JArr(copy)
                } else {
                    val idx = parseArrayIndex(head, node.items.size, allowEnd = false)
                    val copy = ArrayList(node.items)
                    copy[idx] = putAt(copy[idx], tail, value, mode)
                    JsonVal.JArr(copy)
                }
            }
            else -> throw PatchException("Cannot mutate scalar at '$head'")
        }
    }

    private fun removeAt(node: JsonVal, tokens: List<String>): JsonVal {
        if (tokens.isEmpty()) throw PatchException("Cannot remove document root")
        val head = tokens.first()
        val tail = tokens.drop(1)
        return when (node) {
            is JsonVal.JObj -> {
                if (!node.fields.containsKey(head)) throw PatchException("Cannot remove missing key '$head'")
                val copy = LinkedHashMap(node.fields)
                if (tail.isEmpty()) copy.remove(head)
                else copy[head] = removeAt(copy.getValue(head), tail)
                JsonVal.JObj(copy)
            }
            is JsonVal.JArr -> {
                val idx = parseArrayIndex(head, node.items.size, allowEnd = false)
                val copy = ArrayList(node.items)
                if (tail.isEmpty()) copy.removeAt(idx)
                else copy[idx] = removeAt(copy[idx], tail)
                JsonVal.JArr(copy)
            }
            else -> throw PatchException("Cannot remove from scalar at '$head'")
        }
    }

    private fun parseArrayIndex(tok: String, size: Int, allowEnd: Boolean): Int {
        if (tok.length > 1 && tok.startsWith("0")) {
            throw PatchException("RFC 6901 forbids leading zeros in array index: '$tok'")
        }
        val idx = tok.toIntOrNull() ?: throw PatchException("Invalid array index: '$tok'")
        val max = if (allowEnd) size else size - 1
        if (idx < 0 || idx > max) throw PatchException("Array index $idx out of bounds (size=$size)")
        return idx
    }

    fun jsonEquals(a: JsonVal, b: JsonVal): Boolean = when {
        a is JsonVal.JNull && b is JsonVal.JNull -> true
        a is JsonVal.JBool && b is JsonVal.JBool -> a.v == b.v
        a is JsonVal.JNum && b is JsonVal.JNum -> a.v.compareTo(b.v) == 0 || (a.v == 0.0 && b.v == 0.0)
        a is JsonVal.JStr && b is JsonVal.JStr -> a.v == b.v
        a is JsonVal.JArr && b is JsonVal.JArr ->
            a.items.size == b.items.size && a.items.indices.all { jsonEquals(a.items[it], b.items[it]) }
        a is JsonVal.JObj && b is JsonVal.JObj ->
            a.fields.keys == b.fields.keys && a.fields.all { (k, va) -> jsonEquals(va, b.fields.getValue(k)) }
        else -> false
    }

    fun toJson(v: JsonVal): String = when (v) {
        JsonVal.JNull -> "null"
        is JsonVal.JBool -> if (v.v) "true" else "false"
        is JsonVal.JNum -> {
            val asLong = v.v.toLong()
            if (v.v == asLong.toDouble()) asLong.toString() else v.v.toString()
        }
        is JsonVal.JStr -> buildString {
            append('"')
            for (ch in v.v) {
                when (ch) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(ch)
                }
            }
            append('"')
        }
        is JsonVal.JArr -> v.items.joinToString(",", "[", "]") { toJson(it) }
        is JsonVal.JObj -> v.fields.entries.sortedBy { it.key }
            .joinToString(",", "{", "}") { (k, fv) -> "${toJson(JsonVal.JStr(k))}:${toJson(fv)}" }
    }

    fun parseJson(raw: String): JsonVal {
        val p = JsonReader(raw)
        val out = p.readValue(0)
        p.skipWs()
        require(p.pos == raw.length) { "Trailing characters at index ${p.pos}" }
        return out
    }

    fun parsePatchOps(rawJsonArray: String): List<PatchOp> {
        val arr = parseJson(rawJsonArray) as? JsonVal.JArr
            ?: throw PatchException("RFC 6902 patch document must be a JSON array")
        return arr.items.map { item ->
            val obj = item as? JsonVal.JObj ?: throw PatchException("Each patch op must be a JSON object")
            val op = (obj.fields["op"] as? JsonVal.JStr)?.v ?: throw PatchException("Missing 'op'")
            val path = (obj.fields["path"] as? JsonVal.JStr)?.v ?: throw PatchException("Missing 'path'")
            when (op) {
                "add" -> PatchOp.Add(path, obj.fields["value"] ?: throw PatchException("add requires 'value'"))
                "remove" -> PatchOp.Remove(path)
                "replace" -> PatchOp.Replace(path, obj.fields["value"] ?: throw PatchException("replace requires 'value'"))
                "move" -> PatchOp.Move((obj.fields["from"] as? JsonVal.JStr)?.v ?: throw PatchException("move requires 'from'"), path)
                "copy" -> PatchOp.Copy((obj.fields["from"] as? JsonVal.JStr)?.v ?: throw PatchException("copy requires 'from'"), path)
                "test" -> PatchOp.Test(path, obj.fields["value"] ?: throw PatchException("test requires 'value'"))
                else -> throw PatchException("Unknown RFC 6902 op: $op")
            }
        }
    }

    private class JsonReader(private val s: String) {
        var pos = 0

        fun skipWs() {
            while (pos < s.length && s[pos].isWhitespace()) pos++
        }

        fun readValue(depth: Int): JsonVal {
            require(depth <= 64) { "JSON nesting exceeds depth 64" }
            skipWs()
            require(pos < s.length) { "Unexpected EOF in JSON" }
            return when (s[pos]) {
                'n' -> { expect("null"); JsonVal.JNull }
                't' -> { expect("true"); JsonVal.JBool(true) }
                'f' -> { expect("false"); JsonVal.JBool(false) }
                '"' -> JsonVal.JStr(readStr())
                '[' -> readArr(depth + 1)
                '{' -> readObj(depth + 1)
                else -> readNum()
            }
        }

        private fun expect(lit: String) {
            require(s.startsWith(lit, pos)) { "Expected '$lit' at index $pos" }
            pos += lit.length
        }

        private fun readStr(): String {
            pos++ // skip '"'
            val sb = StringBuilder()
            while (pos < s.length) {
                val ch = s[pos++]
                if (ch == '"') return sb.toString()
                if (ch == '\\') {
                    require(pos < s.length) { "Unterminated escape in JSON string" }
                    when (val esc = s[pos++]) {
                        '"', '\\', '/' -> sb.append(esc)
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> {
                            require(pos + 4 <= s.length) { "Invalid \\uXXXX escape" }
                            val cp = s.substring(pos, pos + 4).toInt(16)
                            pos += 4
                            sb.append(cp.toChar())
                        }
                        else -> throw IllegalArgumentException("Invalid JSON escape \\$esc")
                    }
                } else {
                    sb.append(ch)
                }
            }
            throw IllegalArgumentException("Unterminated JSON string")
        }

        private fun readNum(): JsonVal.JNum {
            val start = pos
            if (pos < s.length && s[pos] == '-') pos++
            while (pos < s.length && (s[pos].isDigit() || s[pos] in ".eE+-")) pos++
            val num = s.substring(start, pos).toDoubleOrNull()
                ?: throw IllegalArgumentException("Invalid JSON number at $start")
            return JsonVal.JNum(num)
        }

        private fun readArr(depth: Int): JsonVal.JArr {
            pos++
            skipWs()
            if (pos < s.length && s[pos] == ']') { pos++; return JsonVal.JArr(emptyList()) }
            val items = mutableListOf<JsonVal>()
            while (true) {
                items.add(readValue(depth))
                skipWs()
                require(pos < s.length) { "Unterminated JSON array" }
                if (s[pos] == ']') { pos++; return JsonVal.JArr(items) }
                require(s[pos] == ',') { "Expected ',' in JSON array at $pos" }
                pos++
            }
        }

        private fun readObj(depth: Int): JsonVal.JObj {
            pos++
            skipWs()
            if (pos < s.length && s[pos] == '}') { pos++; return JsonVal.JObj(emptyMap()) }
            val map = LinkedHashMap<String, JsonVal>()
            while (true) {
                skipWs()
                require(pos < s.length && s[pos] == '"') { "Expected string key at $pos" }
                val k = readStr()
                skipWs()
                require(pos < s.length && s[pos] == ':') { "Expected ':' at $pos" }
                pos++
                map[k] = readValue(depth)
                skipWs()
                require(pos < s.length) { "Unterminated JSON object" }
                if (s[pos] == '}') { pos++; return JsonVal.JObj(map) }
                require(s[pos] == ',') { "Expected ',' in JSON object at $pos" }
                pos++
            }
        }
    }
}
