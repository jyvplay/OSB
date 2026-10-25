package dev.fusion.format.pdf

/**
 * Standard-security-handler permission bits (ISO 32000-1 Table 22,
 * 1-based bit positions): 3 print, 4 modify, 5 copy, 6 annotate,
 * 9 fill forms, 10 extract-for-accessibility, 11 assemble,
 * 12 print-high-res. P is a SIGNED 32-bit value in files (high
 * reserved bits set make it negative) — the decoder takes the raw Int.
 *
 * HONESTY: these bits are a POLICY DECLARATION enforced by viewers,
 * not cryptography; this kernel reports what the document asks for
 * and never claims the asks are enforceable.
 */
object PdfPerm {
    const val SCOPE = "Table 22 bit decode of the raw signed P value; viewer-policy declaration, NOT enforcement."

    data class Perms(
        val print: Boolean, val modify: Boolean, val copy: Boolean, val annotate: Boolean,
        val fillForms: Boolean, val extractAccess: Boolean, val assemble: Boolean, val printHiRes: Boolean
    )

    private fun bit(p: Int, oneBased: Int): Boolean = (p shr (oneBased - 1)) and 1 == 1

    fun decode(p: Int): Perms = Perms(
        print = bit(p, 3), modify = bit(p, 4), copy = bit(p, 5), annotate = bit(p, 6),
        fillForms = bit(p, 9), extractAccess = bit(p, 10), assemble = bit(p, 11), printHiRes = bit(p, 12)
    )

    fun encode(perms: Perms): Int {
        var p = -1 shl 12 or 0b11000000       // reserved high bits + bits 7,8 set per Table 22 notes
        fun set(on: Boolean, oneBased: Int) { if (!on) p = p and (1 shl (oneBased - 1)).inv() }
        p = p or 0xFFF
        set(perms.print, 3); set(perms.modify, 4); set(perms.copy, 5); set(perms.annotate, 6)
        set(perms.fillForms, 9); set(perms.extractAccess, 10); set(perms.assemble, 11); set(perms.printHiRes, 12)
        // bits 1-2 reserved must be 0
        p = p and (1 or 2).inv()
        return p
    }

    fun allAllowed(p: Int): Boolean = decode(p).let {
        it.print && it.modify && it.copy && it.annotate && it.fillForms && it.extractAccess && it.assemble && it.printHiRes
    }
}
