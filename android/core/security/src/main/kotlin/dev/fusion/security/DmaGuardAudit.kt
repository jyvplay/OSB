// SPDX-License-Identifier: Apache-2.0
package dev.fusion.security

/**
 * Clean-room hardware Direct Memory Access (DMA) & locked-port peripheral attack posture evaluator.
 *
 * Counters physical Thunderbolt 3/4, USB4, PCIe M.2/ExpressCard (`PCILeech`, `Thunderspy`), and
 * UEFI early-boot DMA memory-extraction vectors:
 * 1. OS IOMMU must enforce strict translation (`iommu.passthrough=0`, `iommu.strict=1`);
 *    `PASSTHROUGH` or `DISABLED` exposes host DRAM to rogue bus masters.
 * 2. Firmware `DMAR`/`IVRS` pre-boot DMA protection opt-in bit must be active so option-ROM /
 *    pre-OS boot stages cannot read/patch kernel memory before the OS IOMMU initializes.
 * 3. External PCIe/Thunderbolt hotplug must deny unauthorized tunnels while locked, and USB-C
 *    data signaling must disable new enumeration on the lock screen (`deny_new_usb`).
 */
object DmaGuardAudit {
    const val SCOPE =
        "Caller-supplied IOMMU, UEFI DMAR/IVRS pre-boot opt-in, external PCIe/Thunderbolt, and locked-screen USB data posture audit; sysfs/ACPI table reading lives in host/device collectors."

    enum class IommuMode { STRICT, LAZY_IOTLB, PASSTHROUGH, DISABLED }

    enum class ExternalPciePolicy { DENY_ALL, AUTHORIZE_WHEN_UNLOCKED, ALLOW_ALWAYS }

    enum class UsbLockPolicy { DISABLED_ALWAYS, DISABLED_WHILE_LOCKED, ENABLED_ALWAYS }

    enum class Verdict { HARDENED, DEGRADED, EXPOSED }

    enum class Code {
        IOMMU_DISABLED_OR_PASSTHROUGH,
        IOMMU_LAZY_IOTLB_WINDOW,
        PREBOOT_DMA_UNPROTECTED,
        PCIE_TUNNEL_UNRESTRICTED,
        USB_DATA_ACTIVE_WHILE_LOCKED,
    }

    data class Signals(
        val iommuMode: IommuMode,
        val preBootDmaProtection: Boolean,
        val externalPciePolicy: ExternalPciePolicy,
        val usbLockPolicy: UsbLockPolicy,
        val hasExternalDmaCapablePorts: Boolean = true,
    )

    data class Report(
        val verdict: Verdict,
        val findings: List<Code>,
        val safeForAmnesicRamSecrets: Boolean,
    )

    fun evaluate(signals: Signals): Report {
        val findings = ArrayList<Code>()
        var critical = false
        var degraded = false

        when (signals.iommuMode) {
            IommuMode.STRICT -> {}
            IommuMode.LAZY_IOTLB -> {
                findings.add(Code.IOMMU_LAZY_IOTLB_WINDOW)
                if (signals.hasExternalDmaCapablePorts) degraded = true
            }
            IommuMode.PASSTHROUGH, IommuMode.DISABLED -> {
                findings.add(Code.IOMMU_DISABLED_OR_PASSTHROUGH)
                if (signals.hasExternalDmaCapablePorts) critical = true else degraded = true
            }
        }

        if (!signals.preBootDmaProtection) {
            findings.add(Code.PREBOOT_DMA_UNPROTECTED)
            if (signals.hasExternalDmaCapablePorts) degraded = true
        }

        when (signals.externalPciePolicy) {
            ExternalPciePolicy.DENY_ALL -> {}
            ExternalPciePolicy.AUTHORIZE_WHEN_UNLOCKED -> {}
            ExternalPciePolicy.ALLOW_ALWAYS -> {
                findings.add(Code.PCIE_TUNNEL_UNRESTRICTED)
                if (signals.hasExternalDmaCapablePorts) critical = true else degraded = true
            }
        }

        if (signals.usbLockPolicy == UsbLockPolicy.ENABLED_ALWAYS) {
            findings.add(Code.USB_DATA_ACTIVE_WHILE_LOCKED)
            degraded = true
        }

        val verdict = when {
            critical -> Verdict.EXPOSED
            degraded -> Verdict.DEGRADED
            else -> Verdict.HARDENED
        }
        return Report(
            verdict = verdict,
            findings = findings,
            safeForAmnesicRamSecrets = verdict == Verdict.HARDENED,
        )
    }
}
