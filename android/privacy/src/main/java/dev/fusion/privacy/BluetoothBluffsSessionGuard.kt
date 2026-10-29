// SPDX-License-Identifier: Apache-2.0
package dev.fusion.privacy

/**
 * Clean-room Bluetooth `BLUFFS` (`CVE-2023-24023`), `KNOB`, and `BIAS` Session-Key & Role-Switch
 * Downgrade Guard (`Vector V11`).
 *
 * Enforces Bluetooth SIG Security Mode 4 Level 4 (`Secure Connections Only`), mandates full
 * `16`-octet (`128`-bit) encryption keys (rejecting `<= 7`-octet KNOB/BLUFFS keys and `8..15`-octet
 * reduced-entropy links), blocks `SC -> LSC` role-switch downgrades on previously SC-bonded peers,
 * and detects repeated session-key derivation nonces (`W_s`) across sessions.
 */
object BluetoothBluffsSessionGuard {
    const val SCOPE =
        "Pure-JVM Bluetooth BLUFFS (CVE-2023-24023) / KNOB / BIAS session validator enforcing Mode 4 Level 4 Secure Connections Only, 16-octet key floor, bond-downgrade refusal, and derivation-nonce replay detection (V11)."

    const val REQUIRED_KEY_OCTETS = 16

    enum class PairingSecurityMode { LEGACY_LSC, SECURE_CONNECTIONS_SC }
    enum class DeviceClass { HID_KEYBOARD_MOUSE, AUDIO_HEADSET, CONTINUITY_PEER, GENERIC }

    data class SessionProposal(
        val peerAddress: String,
        val deviceClass: DeviceClass,
        val negotiatedMode: PairingSecurityMode,
        val negotiatedKeyOctets: Int,
        val authenticated: Boolean,
        val roleSwitchRequestedBeforeAuth: Boolean,
        val derivationNonceHex: String,
    )

    data class GuardVerdict(
        val allowed: Boolean,
        val quarantinedReasons: List<String>,
        val securityLevelLabel: String,
    )

    class BondLedger(private val maxNonceHistoryPerPeer: Int = 64) {
        private val scBondedPeers = HashSet<String>()
        private val seenNonces = HashMap<String, LinkedHashSet<String>>()

        fun recordTrustedScBond(peerAddress: String) {
            scBondedPeers.add(peerAddress.trim().uppercase())
        }

        fun evaluate(p: SessionProposal): GuardVerdict {
            val mac = p.peerAddress.trim().uppercase()
            val reasons = ArrayList<String>()

            if (p.roleSwitchRequestedBeforeAuth) {
                reasons.add("BIAS_ROLE_SWITCH_BEFORE_AUTH")
            }
            if (!p.authenticated) {
                reasons.add("UNAUTHENTICATED_BASEBAND_LINK")
            }
            if (p.negotiatedMode != PairingSecurityMode.SECURE_CONNECTIONS_SC) {
                if (mac in scBondedPeers) {
                    reasons.add("BLUFFS_SC_TO_LSC_DOWNGRADE")
                } else {
                    reasons.add("LEGACY_LSC_REJECTED_MODE4_LEVEL4")
                }
            }
            if (p.negotiatedKeyOctets < REQUIRED_KEY_OCTETS) {
                val tag = if (p.negotiatedKeyOctets <= 7) "KNOB_BLUFFS_WEAK_KEY_${p.negotiatedKeyOctets}B"
                else "SUB_128BIT_KEY_${p.negotiatedKeyOctets}B"
                reasons.add(tag)
            }
            val nonce = p.derivationNonceHex.trim().uppercase()
            if (nonce.length < 16 || !nonce.all { it in '0'..'9' || it in 'A'..'F' }) {
                reasons.add("MALFORMED_DERIVATION_NONCE")
            } else {
                val history = seenNonces.getOrPut(mac) { LinkedHashSet() }
                if (nonce in history) {
                    reasons.add("BLUFFS_NONCE_REUSE_ACROSS_SESSIONS")
                } else if (reasons.isEmpty()) {
                    if (history.size >= maxNonceHistoryPerPeer) {
                        val first = history.iterator().next()
                        history.remove(first)
                    }
                    history.add(nonce)
                    scBondedPeers.add(mac)
                }
            }
            val lvl = if (reasons.isEmpty()) "MODE4_LEVEL4_SC_128BIT" else "QUARANTINED"
            return GuardVerdict(reasons.isEmpty(), reasons, lvl)
        }
    }
}
