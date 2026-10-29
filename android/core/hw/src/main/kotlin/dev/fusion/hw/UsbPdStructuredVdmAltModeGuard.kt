// SPDX-License-Identifier: Apache-2.0
package dev.fusion.hw

/**
 * Vector `#31` — Rogue USB-C Power-Delivery Structured VDM / `EnterUSB` alt-mode injection guard
 * (`USB PD R3.2 V1.2 §6.4.12.2 Table 6.33`, `USB PD 3.1 §6.4.9 EnterUSB`, `§8.3.3.x`).
 *
 * Bit-exact contract enforced before any alt-mode negotiation is allowed to reach the TCPM:
 * - `bit 15 (VDM Type) == 1` selects a **Structured** VDM. An unstructured VDM whose low bits happen to
 *   equal `1` (`Discover Identity`) must never be parsed as structured (`OpenDevicePartnership#101`).
 * - `Command` occupies **bits 4..0** and is a 5-bit field; `32..255` are unencodable and refused
 *   (`OpenDevicePartnership#92`). `16..31` are SVID-specific and gated.
 * - SVID `0x8087` (Thunderbolt / USB4) and vendor DFU entry are blocked while the shell is locked or
 *   the dock is untrusted (`hodgesds/narf#1194` names `EnterUSB` as the production gap).
 * - Power-only contracts (`5V/9V/15V/20V` fixed PDOs) always stay available.
 */
object UsbPdStructuredVdmAltModeGuard {
    const val VDM_TYPE_BIT = 0x8000
    const val STRUCTURED_VMD_VERSION = 0x0000

    const val CMD_DISCOVER_IDENTITY = 0x01
    const val CMD_DISCOVER_SVIDS = 0x02
    const val CMD_DISCOVER_MODES = 0x03
    const val CMD_ENTER_MODE = 0x04
    const val CMD_EXIT_MODE = 0x05
    const val CMD_ATTENTION = 0x06
    const val CMD_SVID_SPECIFIC_MIN = 16
    const val CMD_SVID_SPECIFIC_MAX = 31

    const val SVID_DISPLAYPORT = 0xFF01
    const val SVID_THUNDERBOLT = 0x8087
    const val SVID_USB4 = 0xFF00

    enum class Decision { ALLOW, REFUSE_UNSTRUCTURED, REFUSE_COMMAND_RANGE, REFUSE_SVID_BLOCKED, REFUSE_POWER_ROLE_CHANGE, ALLOW_POWER_ONLY }
    enum class PortState { UNLOCKED_TRUSTED, UNLOCKED_UNTRUSTED, LOCKED }

    data class VdmHeader(
        val svid: Int,
        val vdmTypeStructured: Boolean,
        val version: Int,
        val objectPosition: Int,
        val commandType: Int,
        val command: Int,
    )

    data class Verdict(val decision: Decision, val detail: String, val parsed: VdmHeader?, val allowPowerContract: Boolean)

    fun parseHeader(word: Int): Verdict {
        val svid = word ushr 16 and 0xFFFF
        val structured = (word and VDM_TYPE_BIT) != 0
        val version = word ushr 13 and 0x3
        val objectPos = word ushr 8 and 0x7
        val cmdType = word ushr 6 and 0x3
        val command = word and 0x1F
        val parsed = VdmHeader(svid, structured, version, objectPos, cmdType, command)
        if (!structured) {
            return Verdict(Decision.REFUSE_UNSTRUCTURED, "bit 15 clear: unstructured VDM refused as a structured command", parsed, true)
        }
        if (command in 7..15 || command >= 32) {
            return Verdict(Decision.REFUSE_COMMAND_RANGE, "command $command outside {1..6} U {16..31} (5-bit field)", parsed, true)
        }
        if (version != STRUCTURED_VMD_VERSION) {
            return Verdict(Decision.REFUSE_COMMAND_RANGE, "unsupported structured VDM version $version", parsed, true)
        }
        return Verdict(Decision.ALLOW, "structured VDM accepted for policy evaluation", parsed, true)
    }

    fun evaluate(word: Int, port: PortState): Verdict {
        val parsedVerdict = parseHeader(word)
        if (parsedVerdict.decision != Decision.ALLOW) return parsedVerdict
        val h = parsedVerdict.parsed!!
        val blockedSvids = setOf(SVID_THUNDERBOLT, SVID_USB4)
        return when {
            h.svid in blockedSvids && port != PortState.UNLOCKED_TRUSTED -> Verdict(
                Decision.REFUSE_SVID_BLOCKED,
                "SVID 0x%04x (Thunderbolt/USB4 alt-mode) requires an unlocked trusted dock".format(h.svid),
                h,
                true,
            )
            h.command in CMD_SVID_SPECIFIC_MIN..CMD_SVID_SPECIFIC_MAX && port == PortState.LOCKED -> Verdict(
                Decision.REFUSE_SVID_BLOCKED,
                "SVID-specific command ${h.command} refused while the shell is locked",
                h,
                true,
            )
            h.command == CMD_ENTER_MODE && h.svid != SVID_DISPLAYPORT && port != PortState.UNLOCKED_TRUSTED -> Verdict(
                Decision.REFUSE_SVID_BLOCKED,
                "Enter Mode for SVID 0x%04x requires explicit user trust".format(h.svid),
                h,
                true,
            )
            port == PortState.LOCKED -> Verdict(Decision.ALLOW_POWER_ONLY, "locked shell: data alt-modes suppressed, charging preserved", h, true)
            else -> Verdict(Decision.ALLOW, "structured VDM permitted", h, true)
        }
    }

    /** `EnterUSB` (USB PD 3.1 §6.4.9) data-message admission: never while locked, never for untrusted docks. */
    fun enterUsbAllowed(port: PortState, requestedDataRole: String): Pair<Boolean, String> = when {
        port == PortState.LOCKED -> false to "EnterUSB blocked while locked"
        port == PortState.UNLOCKED_UNTRUSTED -> false to "EnterUSB blocked for untrusted dock"
        requestedDataRole !in setOf("DFP", "UFP", "DRD") -> false to "invalid data role '$requestedDataRole'"
        else -> true to "EnterUSB permitted for $requestedDataRole"
    }

    /** Power-only fallback contract: fixed PDOs always remain negotiable even when data is refused. */
    fun powerOnlyContract(voltagesMv: List<Int>): List<Int> =
        voltagesMv.filter { it in setOf(5_000, 9_000, 15_000, 20_000) }.distinct().sorted()
}
