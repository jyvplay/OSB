// SPDX-License-Identifier: Apache-2.0
package dev.fusion.hw

/**
 * Clean-room USB HID 1.11 §6.2.2 Report Descriptor bytecode parser and Appendix B.1 8-byte
 * Boot Keyboard report-stream analyzer for local hardware-implant & keystroke-injection defense.
 *
 * Counters active USB/BLE hardware implants (`O.MG Cable`, `USB Rubber Ducky`, `Bash Bunny`,
 * composite mouse+keyboard trojans):
 * 1. [parseDescriptor] decodes short HID report-descriptor items (`Main`, `Global` with `Push`/`Pop`
 *    stack, `Local`), enumerating top-level `Application` collections, report IDs, bit widths, and
 *    usage pages, while refusing §6.2.2.3 long items (`0xFE`) and unbalanced collections.
 * 2. Flags composite stealth keyboards (`claimedPrimaryRole != KEYBOARD` yet descriptor exposes
 *    Generic Desktop `Keyboard 0x01/0x06` or `Keypad 0x01/0x07` or `Keyboard Page 0x07` inputs)
 *    and Vendor-Defined raw HID C2 channels (`0xFF00..0xFFFF` with Output/Feature reports).
 * 3. [analyzeBootKeyboardStream] inspects 8-byte boot keyboard reports (`[mods, reserved, k0..k5]`)
 *    for non-zero `reserved` bytes, `ErrorRollOver` (`0x01`) floods, early-enumeration GUI command
 *    chords (`GUI+R`, `GUI+Enter`, `Ctrl+Alt+T`), and `< 8 ms` superhuman keystroke bursts.
 *
 * Honest physical limit: a purely passive electrical `D+/D-` wire tap that does not enumerate an
 * endpoint or modify timing cannot be detected by USB descriptors alone; [recommendsOnScreenRandomPad]
 * stays `true` on untrusted external wired buses.
 */
object HidReportAudit {
    const val SCOPE =
        "USB HID 1.11 §6.2.2 short-item report descriptor parser + 8-byte boot keyboard injection/composite detector; passive electrical bus taps are undetectable in software and flagged via untrustedBus."

    const val MAX_DESCRIPTOR_BYTES = 4_096
    const val MAX_COLLECTION_DEPTH = 16
    const val MAX_PUSH_STACK = 8

    enum class ClaimedRole { KEYBOARD, MOUSE, STORAGE_OR_CHARGER, AUDIO_HEADSET, FIDO_TOKEN }

    enum class RiskFlag {
        UNDECLARED_KEYBOARD_CAPABILITY,
        COMPOSITE_MOUSE_AND_KEYBOARD,
        VENDOR_DEFINED_C2_CHANNEL,
        NON_ZERO_RESERVED_BOOT_BYTE,
        ERROR_ROLLOVER_FLOOD,
        GUI_COMMAND_INJECTION_CHORD,
        SUPERHUMAN_KEYSTROKE_CADENCE,
    }

    data class TopCollection(val usagePage: Int, val usage: Int)

    data class DescriptorSummary(
        val topCollections: List<TopCollection>,
        val usagePagesSeen: Set<Int>,
        val reportIds: Set<Int>,
        val inputBits: Int,
        val outputBits: Int,
        val featureBits: Int,
        val exposesKeyboardInput: Boolean,
        val exposesMouseInput: Boolean,
        val exposesVendorChannel: Boolean,
    )

    data class BootKeyReport(val timestampMs: Long, val bytes: ByteArray) {
        init {
            require(bytes.size == 8) { "USB boot keyboard report must be exactly 8 bytes, got ${bytes.size}" }
            require(timestampMs >= 0L) { "timestampMs must be >= 0" }
        }
    }

    data class AuditVerdict(
        val quarantineDevice: Boolean,
        val flags: Set<RiskFlag>,
        val descriptor: DescriptorSummary,
        val recommendsOnScreenRandomPad: Boolean,
    )

    private data class GlobalState(
        val usagePage: Int = 0,
        val reportSize: Int = 0,
        val reportCount: Int = 0,
        val reportId: Int = 0,
    )

    fun parseDescriptor(bytes: ByteArray): DescriptorSummary {
        require(bytes.isNotEmpty() && bytes.size <= MAX_DESCRIPTOR_BYTES) {
            "HID descriptor size ${bytes.size} outside 1..$MAX_DESCRIPTOR_BYTES"
        }
        var global = GlobalState()
        val pushStack = ArrayList<GlobalState>()
        val localUsages = ArrayList<Int>()
        var collectionDepth = 0
        val topCollections = ArrayList<TopCollection>()
        val pagesSeen = LinkedHashSet<Int>()
        val reportIds = LinkedHashSet<Int>()

        var inputBits = 0L
        var outputBits = 0L
        var featureBits = 0L
        var hasKeyboard = false
        var hasMouse = false
        var hasVendorOutOrFeature = false

        var i = 0
        while (i < bytes.size) {
            val prefix = bytes[i++].toInt() and 0xFF
            require(prefix != 0xFE) { "USB HID §6.2.2.3 long item (0xFE) refused" }
            val sizeCode = prefix and 0x03
            val payloadLen = if (sizeCode == 3) 4 else sizeCode
            require(i + payloadLen <= bytes.size) { "truncated HID item at offset ${i - 1}" }
            val bType = (prefix ushr 2) and 0x03
            val bTag = (prefix ushr 4) and 0x0F

            var data = 0
            for (b in 0 until payloadLen) {
                data = data or ((bytes[i + b].toInt() and 0xFF) shl (8 * b))
            }
            i += payloadLen

            when (bType) {
                0 -> { // Main item
                    val flags = data
                    val isConstant = (flags and 0x01) != 0
                    val bits = global.reportSize.toLong() * global.reportCount.toLong()
                    require(bits in 0..(65_536L)) { "unreasonable HID report field bit width $bits" }
                    when (bTag) {
                        0x08 -> { // Input
                            inputBits += bits
                            if (!isConstant && bits > 0) {
                                if (global.usagePage == 0x07) hasKeyboard = true
                            }
                        }
                        0x09 -> { // Output
                            outputBits += bits
                            if (global.usagePage in 0xFF00..0xFFFF && bits > 0) hasVendorOutOrFeature = true
                        }
                        0x0B -> { // Feature
                            featureBits += bits
                            if (global.usagePage in 0xFF00..0xFFFF && bits > 0) hasVendorOutOrFeature = true
                        }
                        0x0A -> { // Collection
                            require(collectionDepth < MAX_COLLECTION_DEPTH) {
                                "HID collection depth exceeds $MAX_COLLECTION_DEPTH"
                            }
                            val collType = data and 0xFF
                            val u = localUsages.firstOrNull() ?: 0
                            if (collectionDepth == 0 && collType == 0x01) { // Application Collection
                                topCollections.add(TopCollection(global.usagePage, u))
                                if (global.usagePage == 0x01 && (u == 0x06 || u == 0x07)) hasKeyboard = true
                                if (global.usagePage == 0x01 && u == 0x02) hasMouse = true
                                if (global.usagePage in 0xFF00..0xFFFF) hasVendorOutOrFeature = true
                            }
                            collectionDepth++
                        }
                        0x0C -> { // End Collection
                            require(collectionDepth > 0) { "unmatched HID End Collection (0xC0)" }
                            collectionDepth--
                        }
                        else -> throw IllegalArgumentException("unknown HID Main item tag 0x${bTag.toString(16)}")
                    }
                    localUsages.clear()
                }
                1 -> { // Global item
                    when (bTag) {
                        0x00 -> {
                            global = global.copy(usagePage = data and 0xFFFF)
                            pagesSeen.add(global.usagePage)
                        }
                        0x07 -> global = global.copy(reportSize = data)
                        0x08 -> {
                            require(data in 1..255) { "invalid HID Report ID $data" }
                            global = global.copy(reportId = data)
                            reportIds.add(data)
                        }
                        0x09 -> global = global.copy(reportCount = data)
                        0x0A -> {
                            require(pushStack.size < MAX_PUSH_STACK) { "HID Global Push stack overflow" }
                            pushStack.add(global)
                        }
                        0x0B -> {
                            require(pushStack.isNotEmpty()) { "HID Global Pop on empty stack" }
                            global = pushStack.removeAt(pushStack.size - 1)
                        }
                    }
                }
                2 -> { // Local item
                    if (bTag == 0x00) localUsages.add(data and 0xFFFF) // Usage
                }
                else -> throw IllegalArgumentException("reserved HID item type $bType")
            }
        }
        require(collectionDepth == 0) { "unclosed HID Collection (depth=$collectionDepth)" }
        require(inputBits <= 1_048_576 && outputBits <= 1_048_576 && featureBits <= 1_048_576) {
            "HID report bit budget exceeded"
        }
        return DescriptorSummary(
            topCollections = topCollections,
            usagePagesSeen = pagesSeen,
            reportIds = reportIds,
            inputBits = inputBits.toInt(),
            outputBits = outputBits.toInt(),
            featureBits = featureBits.toInt(),
            exposesKeyboardInput = hasKeyboard,
            exposesMouseInput = hasMouse,
            exposesVendorChannel = hasVendorOutOrFeature,
        )
    }

    fun analyzeBootKeyboardStream(
        reports: List<BootKeyReport>,
        enumerationTimestampMs: Long = 0L,
    ): Set<RiskFlag> {
        val flags = LinkedHashSet<RiskFlag>()
        var rolloverFrames = 0
        val keyDownTimes = ArrayList<Long>()
        var prevKeys = emptySet<Int>()

        for (r in reports) {
            require(r.timestampMs >= enumerationTimestampMs) {
                "report timestamp ${r.timestampMs} precedes enumeration $enumerationTimestampMs"
            }
            val mods = r.bytes[0].toInt() and 0xFF
            val reserved = r.bytes[1].toInt() and 0xFF
            if (reserved != 0) flags.add(RiskFlag.NON_ZERO_RESERVED_BOOT_BYTE)

            val keys = LinkedHashSet<Int>()
            var allRollover = true
            for (kIdx in 2..7) {
                val code = r.bytes[kIdx].toInt() and 0xFF
                if (code != 0x01) allRollover = false
                if (code >= 0x04) keys.add(code)
            }
            if (allRollover) rolloverFrames++

            // Detect GUI+R (0x15), GUI+Enter (0x28), or Ctrl+Alt+T (0x17) within 3s of USB plug-in
            val hasGui = (mods and 0x88) != 0
            val hasCtrlAlt = (mods and 0x05) == 0x05 || (mods and 0x50) == 0x50
            if (r.timestampMs - enumerationTimestampMs <= 3_000L) {
                if ((hasGui && (0x15 in keys || 0x28 in keys)) || (hasCtrlAlt && 0x17 in keys)) {
                    flags.add(RiskFlag.GUI_COMMAND_INJECTION_CHORD)
                }
            }

            val newlyPressed = keys - prevKeys
            if (newlyPressed.isNotEmpty()) {
                keyDownTimes.add(r.timestampMs)
            }
            prevKeys = keys
        }

        if (rolloverFrames >= 3) flags.add(RiskFlag.ERROR_ROLLOVER_FLOOD)

        // Human elite typists rarely sustain < 25 ms IKI across 8+ consecutive distinct keydowns;
        // microcontroller implants routinely emit < 8 ms intervals.
        if (keyDownTimes.size >= 8) {
            val deltas = ArrayList<Long>(keyDownTimes.size - 1)
            for (k in 1 until keyDownTimes.size) {
                val d = keyDownTimes[k] - keyDownTimes[k - 1]
                require(d >= 0L) { "out-of-order boot keyboard report timestamps" }
                deltas.add(d)
            }
            deltas.sort()
            val medianDelta = deltas[deltas.size / 2]
            if (medianDelta < 8L) {
                flags.add(RiskFlag.SUPERHUMAN_KEYSTROKE_CADENCE)
            }
        }
        return flags
    }

    fun auditDevice(
        claimedRole: ClaimedRole,
        descriptorBytes: ByteArray,
        bootReports: List<BootKeyReport> = emptyList(),
        enumerationTimestampMs: Long = 0L,
        externalUntrustedWiredBus: Boolean = true,
    ): AuditVerdict {
        val summary = parseDescriptor(descriptorBytes)
        val flags = LinkedHashSet<RiskFlag>()

        if (summary.exposesKeyboardInput && claimedRole != ClaimedRole.KEYBOARD) {
            flags.add(RiskFlag.UNDECLARED_KEYBOARD_CAPABILITY)
        }
        if (summary.exposesKeyboardInput && summary.exposesMouseInput) {
            flags.add(RiskFlag.COMPOSITE_MOUSE_AND_KEYBOARD)
        }
        if (summary.exposesVendorChannel && summary.exposesKeyboardInput) {
            flags.add(RiskFlag.VENDOR_DEFINED_C2_CHANNEL)
        }
        flags.addAll(analyzeBootKeyboardStream(bootReports, enumerationTimestampMs))

        val quarantine = RiskFlag.UNDECLARED_KEYBOARD_CAPABILITY in flags ||
            RiskFlag.GUI_COMMAND_INJECTION_CHORD in flags ||
            RiskFlag.SUPERHUMAN_KEYSTROKE_CADENCE in flags ||
            RiskFlag.NON_ZERO_RESERVED_BOOT_BYTE in flags ||
            RiskFlag.VENDOR_DEFINED_C2_CHANNEL in flags

        return AuditVerdict(
            quarantineDevice = quarantine,
            flags = flags,
            descriptor = summary,
            recommendsOnScreenRandomPad = quarantine || externalUntrustedWiredBus,
        )
    }
}
