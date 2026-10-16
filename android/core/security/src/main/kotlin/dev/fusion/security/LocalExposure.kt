package dev.fusion.security

/** Snapshot of limited OS observations. Absence is NOT absence of surveillance or a safe-host verdict.
 * No device names, descriptors, package names, serials or text payload belong in this record.
 */
data class LocalExposure(
    val accessibilityServices: Int?,
    val externalKeyboards: Int?,
    val additionalDisplays: Int?,
    val adbEnabled: Boolean?,
    val developerOptions: Boolean?
) {
    init { require(listOf(accessibilityServices, externalKeyboards, additionalDisplays).all { it == null || it >= 0 }) }

    fun describe(): String {
        fun count(value: Int?) = value?.toString() ?: "UNKNOWN (unavailable)"
        fun setting(value: Boolean?) = when (value) { true -> "enabled"; false -> "disabled"; null -> "UNKNOWN (unavailable)" }
        return """Local exposure snapshot — NOT a malware/keylogger scan
Enabled accessibility services: ${count(accessibilityServices)}
External keyboard-capable devices: ${count(externalKeyboards)}
Additional Android displays: ${count(additionalDisplays)}
ADB setting: ${setting(adbEnabled)}
Developer-options setting: ${setting(developerOptions)}

Counts are observations, not proof of an attacker. Zero/disabled does NOT establish a trustworthy device. An inline keyboard logger or compromised firmware/host may be invisible. Settings are snapshots, not continuous enforcement; wireless debugging is NOT comprehensively inspected.

The ordinary scratchpad uses your chosen Android keyboard. A no-learning request cannot prevent a malicious IME reading text. Touch-only code entry avoids that keyboard input route, but accessibility tools, Android, screen/pointer observers, cameras and the laptop/hypervisor remain trusted. Additional displays do not prove screen capture; zero does not exclude it.

For sensitive work: inspect cables/peripherals; use a physically trusted device; remove unneeded USB/Bluetooth input devices; review accessibility, keyboards, screen-sharing and debugging in system settings. Turn off debugging after your Android Studio evaluation. Never disable input devices blindly. This app changes none of those settings and makes no global anonymity claim."""
    }
}
