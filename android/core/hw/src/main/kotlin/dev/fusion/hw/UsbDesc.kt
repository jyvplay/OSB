package dev.fusion.hw

/**
 * USB standard device descriptor parser (USB 2.0 specification §9.6.1,
 * 18-byte layout; field names follow the spec).
 *
 * This feeds the input-path audit: a device descriptor states its claimed
 * identity (idVendor/idProduct, class triplet, bcdUSB) BEFORE any interface
 * enumerates. bDeviceClass 0x00 defers class to the interfaces — so a
 * descriptor alone can never prove or disprove "is a keyboard"; that
 * honesty is encoded in [classAtInterfaceLevel], not hidden.
 *
 * Declared narrowings: the 18-byte device descriptor only — no
 * configuration/interface/HID descriptors, no live bus access (bytes come
 * from stored transcripts or files), no vendor-name database.
 */
object UsbDesc {
    const val SCOPE = "18-byte USB device descriptor fields only; offline bytes; cannot identify keyboards by itself."
    const val LENGTH = 18
    const val TYPE_DEVICE = 1

    data class Device(
        val bcdUsb: Int, val deviceClass: Int, val deviceSubClass: Int, val deviceProtocol: Int,
        val maxPacketSize0: Int, val idVendor: Int, val idProduct: Int, val bcdDevice: Int,
        val iManufacturer: Int, val iProduct: Int, val iSerialNumber: Int, val numConfigurations: Int
    ) {
        val classAtInterfaceLevel: Boolean get() = deviceClass == 0x00
        val usbVersion: String get() = bcd(bcdUsb)
        val deviceVersion: String get() = bcd(bcdDevice)
    }

    internal fun bcd(v: Int): String {
        val major = ((v shr 12) and 0xF) * 10 + ((v shr 8) and 0xF)
        val minor = (v shr 4) and 0xF
        val sub = v and 0xF
        return if (sub == 0) "$major.$minor" else "$major.$minor$sub"
    }

    private fun u8(b: ByteArray, i: Int) = b[i].toInt() and 0xFF
    private fun u16(b: ByteArray, i: Int) = u8(b, i) or (u8(b, i + 1) shl 8)

    fun parse(bytes: ByteArray): Device {
        require(bytes.size >= LENGTH) { "device descriptor needs $LENGTH bytes, got ${bytes.size}" }
        require(u8(bytes, 0) == LENGTH) { "bLength ${u8(bytes, 0)} != $LENGTH" }
        require(u8(bytes, 1) == TYPE_DEVICE) { "bDescriptorType ${u8(bytes, 1)} is not DEVICE(1)" }
        val bcdUsb = u16(bytes, 2)
        val mps = u8(bytes, 7)
        if (bcdUsb < 0x0300) {
            require(mps in intArrayOf(8, 16, 32, 64)) { "bMaxPacketSize0 $mps invalid below USB 3.0" }
        } else {
            require(mps == 9) { "bMaxPacketSize0 $mps must be 9 (2^9) for USB 3.x" }
        }
        val numCfg = u8(bytes, 17)
        require(numCfg >= 1) { "bNumConfigurations must be >= 1" }
        return Device(
            bcdUsb = bcdUsb,
            deviceClass = u8(bytes, 4), deviceSubClass = u8(bytes, 5), deviceProtocol = u8(bytes, 6),
            maxPacketSize0 = mps,
            idVendor = u16(bytes, 8), idProduct = u16(bytes, 10), bcdDevice = u16(bytes, 12),
            iManufacturer = u8(bytes, 14), iProduct = u8(bytes, 15), iSerialNumber = u8(bytes, 16),
            numConfigurations = numCfg
        )
    }

    fun vidPid(d: Device): String =
        "%04x:%04x".format(d.idVendor, d.idProduct)
}
