package dev.fusion.lens

/**
 * QR symbol-version geometry (ISO/IEC 18004): version v in 1..40 has
 * side length 17 + 4v modules; the inverse rejects sizes off the
 * lattice. Finder patterns are always 3; version information blocks
 * exist only from version 7.
 *
 * Declared narrowings: geometry only — capacity tables, masking and
 * Reed-Solomon live elsewhere (and Micro QR is refused by name, not
 * silently conflated).
 */
object QrVersion {
    const val SCOPE = "17+4v size law + inverse + structural counts; capacities/EC/Micro QR out of scope."

    fun size(version: Int): Int {
        require(version in 1..40) { "version $version outside 1..40 (Micro QR is out of scope)" }
        return 17 + 4 * version
    }

    fun versionForSize(modules: Int): Int {
        require(modules in 21..177 && (modules - 17) % 4 == 0) { "side $modules is not on the QR lattice" }
        return (modules - 17) / 4
    }

    fun hasVersionInfo(version: Int): Boolean {
        require(version in 1..40) { "version $version" }
        return version >= 7
    }

    val FINDER_COUNT = 3
}
