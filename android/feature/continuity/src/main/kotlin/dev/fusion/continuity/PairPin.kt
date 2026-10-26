package dev.fusion.continuity

/**
 * Pairing-PIN check digits via the Damm algorithm (Damm 2004: a totally
 * anti-symmetric quasigroup of order 10; detects ALL single-digit
 * errors and ALL adjacent transpositions, with no 10-vs-X edge case —
 * the fourth checksum family in this repo after Luhn, MRZ 7-3-1 and
 * GS1 1-3, each with different guarantees).
 *
 * The table below is the standard published order-10 TA-quasigroup.
 * A sequence is valid when the final interim digit is 0; the check
 * digit for a body is the interim after processing the body.
 *
 * Declared narrowings: digit strings only; PIN DISPLAY/entropy policy
 * is the session layer's concern — a check digit is error DETECTION,
 * never security, and the scope string says so.
 */
object PairPin {
    const val SCOPE = "Damm check digits: error detection for typed pairing codes, NEVER a security property."

    private val TABLE = arrayOf(
        intArrayOf(0, 3, 1, 7, 5, 9, 8, 6, 4, 2),
        intArrayOf(7, 0, 9, 2, 1, 5, 4, 8, 6, 3),
        intArrayOf(4, 2, 0, 6, 8, 7, 1, 3, 5, 9),
        intArrayOf(1, 7, 5, 0, 9, 8, 3, 4, 2, 6),
        intArrayOf(6, 1, 2, 3, 0, 4, 5, 9, 7, 8),
        intArrayOf(3, 6, 7, 4, 2, 0, 9, 5, 8, 1),
        intArrayOf(5, 8, 6, 9, 7, 2, 0, 1, 3, 4),
        intArrayOf(8, 9, 4, 5, 3, 6, 2, 0, 1, 7),
        intArrayOf(9, 4, 3, 8, 6, 1, 7, 2, 0, 5),
        intArrayOf(2, 5, 8, 1, 4, 3, 6, 7, 9, 0)
    )

    fun interim(digits: String): Int {
        require(digits.isNotEmpty() && digits.all { it.isDigit() }) { "'$digits' is not a digit string" }
        var x = 0
        for (c in digits) x = TABLE[x][c - '0']
        return x
    }

    fun checkDigit(body: String): Int = interim(body)

    fun isValid(full: String): Boolean =
        full.isNotEmpty() && full.all { it.isDigit() } && interim(full) == 0

    fun withCheck(body: String): String = body + checkDigit(body)
}
