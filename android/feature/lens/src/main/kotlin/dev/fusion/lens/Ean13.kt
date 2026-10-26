package dev.fusion.lens

/**
 * EAN-13 / UPC-A check digits for the lens capture surface (GS1 General
 * Specifications §7.9: weights alternate 1,3 from the LEFT of the
 * 12-digit body; check digit = (10 - sum mod 10) mod 10).
 *
 * UPC-A is the 12-digit code; its EAN-13 form is the same digits with
 * a leading 0 — [upcToEan] makes that explicit instead of letting
 * callers guess.
 *
 * Distinct from [Mrz] (7-3-1 weights) and the Luhn kernel (doubling
 * with digit-sum): three different checksum algebras, three kernels.
 *
 * Declared narrowings: numeric validation only — no barcode imaging,
 * no GS1 prefix database (country/company lookup is data, not logic),
 * no EAN-8/ITF-14 yet (refused by length, named here so adding them is
 * a decision).
 */
object Ean13 {
    const val SCOPE = "EAN-13/UPC-A check-digit algebra only; no imaging, no prefix database, EAN-8 refused by length."

    fun checkDigit(body12: String): Int {
        require(body12.length == 12 && body12.all { it.isDigit() }) { "need exactly 12 digits, got '${body12.take(16)}'" }
        var sum = 0
        for (i in body12.indices) {
            val d = body12[i] - '0'
            sum += if (i % 2 == 0) d else d * 3
        }
        return (10 - sum % 10) % 10
    }

    fun isValid(ean13: String): Boolean {
        if (ean13.length != 13 || !ean13.all { it.isDigit() }) return false
        return checkDigit(ean13.substring(0, 12)) == ean13[12] - '0'
    }

    fun validate(ean13: String): String {
        require(ean13.length == 13) { "EAN-13 needs 13 digits, got ${ean13.length} (EAN-8 is out of declared scope)" }
        require(ean13.all { it.isDigit() }) { "non-digit in '$ean13'" }
        require(isValid(ean13)) { "check digit fails for '$ean13'" }
        return ean13
    }

    fun upcToEan(upcA: String): String {
        require(upcA.length == 12 && upcA.all { it.isDigit() }) { "UPC-A needs 12 digits" }
        val ean = "0$upcA"
        require(isValid(ean)) { "UPC-A check digit fails" }
        return ean
    }
}
