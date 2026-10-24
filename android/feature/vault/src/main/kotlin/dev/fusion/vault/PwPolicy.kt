package dev.fusion.vault

/**
 * Password POLICY checking (length and character-class requirements)
 * — deliberately distinct from strength estimation: ShannonBits owns
 * entropy, and this kernel's scope string states the orthodoxy
 * plainly: policy compliance is NOT strength (aaaaaaaaaaaA1! passes
 * most policies and is weak; a long diceware phrase may fail them
 * and be strong).
 *
 * Checks are pure and reported individually so UIs can show which
 * requirement failed without ever logging the password (the API
 * returns booleans and counts, never substrings).
 *
 * Declared narrowings: ASCII class definitions (letter/digit/symbol
 * by code point below 128; non-ASCII counts as a letter class of its
 * own and satisfies "letter" — declared); no breach checking here
 * (HibpRange owns that).
 */
object PwPolicy {
    const val SCOPE = "Policy compliance only — explicitly NOT strength; returns booleans/counts, never substrings."

    data class Policy(
        val minLength: Int, val maxLength: Int = 256,
        val requireUpper: Boolean = false, val requireLower: Boolean = false,
        val requireDigit: Boolean = false, val requireSymbol: Boolean = false
    ) {
        init {
            require(minLength >= 1 && maxLength >= minLength) { "bad length window $minLength..$maxLength" }
        }
    }

    data class Report(
        val lengthOk: Boolean, val upperOk: Boolean, val lowerOk: Boolean,
        val digitOk: Boolean, val symbolOk: Boolean
    ) {
        val pass: Boolean get() = lengthOk && upperOk && lowerOk && digitOk && symbolOk
    }

    fun check(password: CharArray, policy: Policy): Report {
        require(password.isNotEmpty()) { "empty password" }
        var upper = false; var lower = false; var digit = false; var symbol = false
        for (c in password) {
            when {
                c.isUpperCase() -> upper = true
                c.isLowerCase() -> lower = true
                c.isDigit() -> digit = true
                c.code in 0x21..0x7E -> symbol = true
                c.code >= 0x80 -> lower = true   // non-ASCII satisfies the letter classes (declared)
            }
        }
        return Report(
            lengthOk = password.size in policy.minLength..policy.maxLength,
            upperOk = !policy.requireUpper || upper,
            lowerOk = !policy.requireLower || lower,
            digitOk = !policy.requireDigit || digit,
            symbolOk = !policy.requireSymbol || symbol
        )
    }
}
