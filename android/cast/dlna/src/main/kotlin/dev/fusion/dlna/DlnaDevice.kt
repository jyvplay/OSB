// SPDX-License-Identifier: Apache-2.0
package dev.fusion.dlna

/**
 * Bounded DLNA device parser. Extracts device type, friendly name,
 * manufacturer, model name, and presentation URL from a minimal
 * DLNA/UPnP device description subset. Only the root device element
 * and its direct children are parsed — embedded devices, service lists,
 * and event subscription are not handled.
 *
 * Declared narrowings: input at most [MAX_INPUT] chars; field values at
 * most [MAX_FIELD_LEN] chars; only well-formed XML with the exact element
 * names below is accepted; malformed XML is refused, not tolerated.
 * No network fetch — XML is supplied by the caller.
 */
class DlnaDevice private constructor(
    private val deviceType: String?,
    private val friendlyName: String?,
    private val manufacturer: String?,
    private val modelName: String?,
    private val presentationUrl: String?
) {
    companion object {
        const val MAX_INPUT = 1 << 14  // 16 KiB
        const val MAX_FIELD_LEN = 1024
        private val TAG_RE = Regex("<([a-zA-Z][a-zA-Z0-9]*)>([^<]*)</\\1>")

        data class Device(
            val deviceType: String?,
            val friendlyName: String?,
            val manufacturer: String?,
            val modelName: String?,
            val presentationUrl: String?
        )

        fun empty(): DlnaDevice = DlnaDevice(null, null, null, null, null)
    }

    fun parse(xml: String): Device {
        require(xml.length <= MAX_INPUT) { "XML exceeds $MAX_INPUT chars" }
        val fields = mutableMapOf<String, String>()
        for (m in TAG_RE.findAll(xml)) {
            val tag = m.groupValues[1]
            val value = m.groupValues[2]
            if (value.length <= MAX_FIELD_LEN) {
                fields[tag] = value.trim()
            }
        }
        return Device(
            fields["deviceType"],
            fields["friendlyName"],
            fields["manufacturer"],
            fields["modelName"],
            fields["presentationURL"]
        )
    }

    fun isMediaServer(xml: String): Boolean {
        val dev = parse(xml)
        return dev.deviceType != null && dev.deviceType!!.contains("MediaServer")
    }

    fun isMediaRenderer(xml: String): Boolean {
        val dev = parse(xml)
        return dev.deviceType != null && dev.deviceType!!.contains("MediaRenderer")
    }
}
