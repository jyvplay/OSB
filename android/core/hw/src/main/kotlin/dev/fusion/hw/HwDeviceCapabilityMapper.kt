// SPDX-License-Identifier: Apache-2.0
package dev.fusion.hw

/**
 * Production-grade USB interface class & power-draw capability classifier.
 */
class HwDeviceCapabilityMapper private constructor() {
    companion object {
        enum class DeviceClass { AUDIO, HID, MASS_STORAGE, VIDEO, OTHER }

        data class UsbCapability(
            val deviceClass: DeviceClass,
            val maxPowerMa: Int,
            val requiresExternalPower: Boolean
        )

        fun classify(interfaceClass: Int, maxPowerMa: Int): UsbCapability {
            require(interfaceClass in 0..255 && maxPowerMa >= 0) { "invalid USB descriptor" }
            val cls = when (interfaceClass) {
                0x01 -> DeviceClass.AUDIO
                0x03 -> DeviceClass.HID
                0x08 -> DeviceClass.MASS_STORAGE
                0x0E -> DeviceClass.VIDEO
                else -> DeviceClass.OTHER
            }
            return UsbCapability(cls, maxPowerMa, maxPowerMa > 500)
        }
    }
}
