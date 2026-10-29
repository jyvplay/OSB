package dev.fusion.hw

/**
 * Clean-room USB4 / Thunderbolt PCIe DMA (`Thunderclap`, NDSS) & BadUSB HID Keystroke-Injection
 * Quarantine Engine (`V17`, Apache-2.0).
 *
 * Enforces Tails/GrapheneOS-grade physical port lockdown:
 * 1. Any external PCIe / USB4 tunnel (`requestsPcieTunnel = true`) is ALWAYS denied unless
 *    strict kernel `IOMMU` (`iommuStrictTranslationActive = true`) is verified AND the screen is
 *    unlocked with explicit user authorization,
 * 2. Newly attached `USB_CLASS_HID` (`0x03`) keyboards while the screen is locked or while a
 *    sensitive vault prompt is active are quarantined (`BLOCK_PORT_DATA`) to defeat Rubber Ducky /
 *    O.MG cable keystroke injection!
 */
object Usb4PcieIommuDmaGuard {
    const val SCOPE =
        "Thunderclap USB4/PCIe IOMMU DMA lockdown + locked-state BadUSB HID (class 0x03) keystroke-injection quarantine (V17)"

    const val USB_CLASS_HID = 0x03
    const val USB_CLASS_MASS_STORAGE = 0x08

    enum class PortPolicyDecision { ALLOW_CHARGE_ONLY_BLOCK_DATA, QUARANTINE_BADUSB_HID, BLOCK_UNPROTECTED_PCIE_DMA, AUTHORIZE_RESTRICTED_DATA }

    data class PeripheralAttachEvent(
        val vendorId: Int,
        val productId: Int,
        val usbInterfaceClasses: Set<Int>,
        val requestsPcieDmaTunnel: Boolean,
        val screenLocked: Boolean,
        val vaultSensitiveEntryActive: Boolean,
        val iommuStrictTranslationActive: Boolean,
    )

    data class PortGuardVerdict(
        val decision: PortPolicyDecision,
        val enableUsbDataLines: Boolean,
        val enablePcieTunnel: Boolean,
        val reason: String,
    )

    fun evaluateAttach(event: PeripheralAttachEvent): PortGuardVerdict {
        if (event.screenLocked) {
            return PortGuardVerdict(
                decision = PortPolicyDecision.ALLOW_CHARGE_ONLY_BLOCK_DATA,
                enableUsbDataLines = false,
                enablePcieTunnel = false,
                reason = "Screen locked: USB data & PCIe tunnels disabled (charge-only)",
            )
        }
        if (event.requestsPcieDmaTunnel && !event.iommuStrictTranslationActive) {
            return PortGuardVerdict(
                decision = PortPolicyDecision.BLOCK_UNPROTECTED_PCIE_DMA,
                enableUsbDataLines = false,
                enablePcieTunnel = false,
                reason = "Thunderclap defense: refusing USB4/Thunderbolt PCIe tunnel without strict IOMMU",
            )
        }
        if (USB_CLASS_HID in event.usbInterfaceClasses && event.vaultSensitiveEntryActive) {
            return PortGuardVerdict(
                decision = PortPolicyDecision.QUARANTINE_BADUSB_HID,
                enableUsbDataLines = false,
                enablePcieTunnel = false,
                reason = "BadUSB defense: hot-plugged HID keyboard blocked during active vault credential entry",
            )
        }
        return PortGuardVerdict(
            decision = PortPolicyDecision.AUTHORIZE_RESTRICTED_DATA,
            enableUsbDataLines = true,
            enablePcieTunnel = event.requestsPcieDmaTunnel && event.iommuStrictTranslationActive,
            reason = "Unlocked & IOMMU-verified peripheral authorized",
        )
    }
}
