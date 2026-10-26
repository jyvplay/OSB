package dev.fusion.capabilities

/**
 * Android API-level naming table (the public SDK_INT ↔ version/
 * codename mapping, pinned 21..36 as published through Android 16
 * "Baklava"). CapabilityGate owns runtime GATING; this kernel owns
 * the immutable table UIs print.
 *
 * Declared narrowings: levels outside the pinned table REFUSE rather
 * than guess (naming for unreleased levels is marketing, not data —
 * API 37 stays refused until its name is published and pinned here).
 */
object ApiName {
    const val SCOPE = "Pinned 21..36 naming table; unreleased levels refused, never guessed."

    data class Entry(val level: Int, val version: String, val codename: String)

    private val TABLE = listOf(
        Entry(21, "5.0", "Lollipop"), Entry(22, "5.1", "Lollipop"),
        Entry(23, "6.0", "Marshmallow"), Entry(24, "7.0", "Nougat"), Entry(25, "7.1", "Nougat"),
        Entry(26, "8.0", "Oreo"), Entry(27, "8.1", "Oreo"), Entry(28, "9", "Pie"),
        Entry(29, "10", "Q"), Entry(30, "11", "R"), Entry(31, "12", "S"), Entry(32, "12L", "Sv2"),
        Entry(33, "13", "Tiramisu"), Entry(34, "14", "UpsideDownCake"),
        Entry(35, "15", "VanillaIceCream"), Entry(36, "16", "Baklava")
    ).associateBy { it.level }

    fun of(level: Int): Entry = TABLE[level]
        ?: throw IllegalArgumentException("API $level outside the pinned 21..36 table — refused, not guessed")

    fun supports(deviceLevel: Int, requiredLevel: Int): Boolean {
        require(requiredLevel in TABLE) { "required level must be in the pinned table" }
        require(deviceLevel >= 1) { "device level $deviceLevel" }
        return deviceLevel >= requiredLevel
    }

    val MIN = 21
    val MAX = 36
}
