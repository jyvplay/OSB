package dev.fusion.ooxml

/**
 * Worksheet protection: the `<sheetProtection>` record (ECMA-376 Part 1, §18.3.1.85), the per-cell
 * `<protection>` child of an `xf` (§18.8.33), and the **legacy 16-bit password hash** Excel stores in the
 * `password` attribute.
 *
 * What this can and cannot mean, stated once:
 *
 * * the record and the hash are *bytes*. Writing them is verified here;
 * * enforcement is the application's. Nothing in this file prevents an edit, and no test claims a cell was
 *   rejected. A protected sheet is a request to the reader, not a lock, and this hash is not cryptography.
 *
 * The model stores **which actions are blocked**, not the raw attribute value, because the schema's defaults
 * are not uniform: eleven of the fifteen actions default to *blocked* and four (objects, scenarios, selecting
 * locked cells, selecting unlocked cells) default to *allowed*. Storing the schema's polarity directly makes
 * every call site an inversion error waiting to happen, so [Action.defaultBlocked] records the schema once
 * and the writer emits all fifteen attributes explicitly — no reader has to guess a default.
 */
object XlsxProtection {

    /** CT_SheetProtection booleans, in the schema's attribute order. */
    enum class Action(val attr: String, val defaultBlocked: Boolean) {
        OBJECTS("objects", false),
        SCENARIOS("scenarios", false),
        FORMAT_CELLS("formatCells", true),
        FORMAT_COLUMNS("formatColumns", true),
        FORMAT_ROWS("formatRows", true),
        INSERT_COLUMNS("insertColumns", true),
        INSERT_ROWS("insertRows", true),
        INSERT_HYPERLINKS("insertHyperlinks", true),
        DELETE_COLUMNS("deleteColumns", true),
        DELETE_ROWS("deleteRows", true),
        SELECT_LOCKED_CELLS("selectLockedCells", false),
        SORT("sort", true),
        AUTO_FILTER("autoFilter", true),
        PIVOT_TABLES("pivotTables", true),
        SELECT_UNLOCKED_CELLS("selectUnlockedCells", false),
        ;

        companion object {
            fun of(attr: String): Action? = values().firstOrNull { it.attr == attr }
        }
    }

    /** What Excel blocks when a user protects a sheet with its default options. */
    val DEFAULT_BLOCKED: Set<Action> = Action.values().filter { it.defaultBlocked }.toSet()

    private val HASH = Regex("^[0-9A-F]{1,8}$")

    /**
     * A protected sheet. The model holds the **hash**, never the plaintext: a caller with a password uses
     * [ofPassword] and does not keep the password in the document model. `passwordHash` is `null` for
     * "protect without a password", which Excel allows and which writes no `password` attribute at all.
     */
    data class SheetProtection(
        val passwordHash: String? = null,
        val blocked: Set<Action> = DEFAULT_BLOCKED,
    ) {
        init {
            passwordHash?.let {
                require(HASH.matches(it)) {
                    "Sheet-protection password hash must be 1..8 upper-case hex digits: $it"
                }
            }
        }

        companion object {
            /** Protects the sheet with a password, storing only its legacy hash. */
            fun ofPassword(password: String): SheetProtection = SheetProtection(hash(password))
        }
    }

    private fun esc(s: String): String = buildString(s.length) {
        for (ch in s) {
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                else -> append(ch)
            }
        }
    }

    /**
     * Excel's legacy sheet-protection hash: a 16-bit rotate-and-xor over the password's UTF-16 code units,
     * with the rotation distance taken from the character's position, then mixed with the password length and
     * the constant `0xCE4B`, and spelled as four upper-case hex digits.
     *
     * Two boundary conditions are refused rather than approximated, because both would make this
     * implementation disagree with every other one: Excel's hash is defined over at most **15 characters**,
     * and over UTF-16 code units, so an astral character has no representation in it.
     *
     * This is not a cryptographic hash and is not treated as one: it stops an accidental edit, not a
     * determined one — the whole 16-bit space is trivially searchable.
     */
    fun hash(password: String): String {
        require(password.length <= 15) { "The legacy hash covers 15 characters, got ${password.length}" }
        // A surrogate code unit is half of an astral character: Python iterates code points and this code
        // iterates UTF-16 units, so accepting one would make the two implementations disagree.
        require(password.none { it.isSurrogate() }) { "The legacy hash has no representation for astral characters" }
        var value = 0L
        password.forEachIndexed { index, ch ->
            val shifted = ch.code.toLong() shl (index + 1)
            value = value xor ((shifted and 0x7FFF) or (shifted ushr 15))
        }
        value = value xor password.length.toLong()
        value = value xor 0xCE4B
        return (value and 0xFFFF).toString(16).uppercase().padStart(4, '0')
    }

    /**
     * Serialises the record. Every action attribute is written, so the emitted bytes state the whole policy
     * rather than relying on a reader's defaults; `sheet="1"` is what makes the record active at all.
     */
    fun toXml(protection: SheetProtection): String = buildString {
        append("<sheetProtection")
        protection.passwordHash?.let { append(" password=\"" + esc(it) + "\"") }
        append(""" sheet="1"""")
        for (action in Action.values()) {
            append(""" ${action.attr}="${if (action in protection.blocked) 1 else 0}"""")
        }
        append("/>")
    }

    /**
     * Reads `<sheetProtection>` from a worksheet root. A record with `sheet="0"` (protection switched off) is
     * not protection and reads back as `null`; so does an absent element. An unknown attribute is ignored
     * rather than guessed at, and a `password` value that is not hex is dropped: the hash cannot be inverted,
     * so keeping a nonsense value would claim a password this model could never have written.
     */
    fun parse(root: BoundedXml.Node): SheetProtection? {
        val node = root.children.firstOrNull { it.name.local == "sheetProtection" } ?: return null
        if (!isOn(node.attr("sheet"))) return null
        val blocked = LinkedHashSet<Action>()
        for (action in Action.values()) {
            val raw = node.attr(action.attr)
            val value = if (raw == null) action.defaultBlocked else isOn(raw)
            if (value) blocked.add(action)
        }
        val passwordHash = node.attr("password")?.uppercase()?.takeIf { HASH.matches(it) }
        return SheetProtection(passwordHash, blocked)
    }

    private fun isOn(value: String?): Boolean = value == "1" || value.equals("true", ignoreCase = true)
}
