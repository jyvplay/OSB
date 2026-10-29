// SPDX-License-Identifier: Apache-2.0
package dev.fusion.ooxml

/**
 * Pure-JVM OOXML WordprocessingML (`word/document.xml`) `<w:sdt>` Structured Document Tag
 * (Content Control) builder, form-field binder, and round-trip extractor (`PLAIN_TEXT`,
 * `DROP_DOWN_LIST`, `CHECKBOX`, `DATE_PICKER`).
 */
object DocxContentControlBinder {
    const val SCOPE =
        "OOXML WordprocessingML w:sdt Structured Document Tag (PLAIN_TEXT, DROP_DOWN_LIST, CHECKBOX, DATE_PICKER) builder, data-binding injector, and round-trip extractor."

    enum class ControlKind { PLAIN_TEXT, DROP_DOWN_LIST, CHECKBOX, DATE_PICKER }

    data class SdtControlSpec(
        val tag: String,
        val alias: String,
        val kind: ControlKind,
        val currentValue: String,
        val dropDownItems: List<String> = emptyList(),
        val checked: Boolean = false,
    )

    fun serializeControlsXml(controls: List<SdtControlSpec>): String {
        val sb = StringBuilder()
        sb.append("<w:body xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\" xmlns:w14=\"http://schemas.microsoft.com/office/word/2010/wordml\">")
        for (c in controls) {
            sb.append("<w:sdt><w:sdtPr>")
            sb.append("<w:alias w:val=\"").append(esc(c.alias)).append("\"/>")
            sb.append("<w:tag w:val=\"").append(esc(c.tag)).append("\"/>")
            when (c.kind) {
                ControlKind.PLAIN_TEXT -> sb.append("<w:text/>")
                ControlKind.DATE_PICKER -> sb.append("<w:date><w:dateFormat w:val=\"yyyy-MM-dd\"/></w:date>")
                ControlKind.CHECKBOX -> {
                    val v = if (c.checked) "1" else "0"
                    sb.append("<w14:checkbox><w14:checked w14:val=\"").append(v).append("\"/></w14:checkbox>")
                }
                ControlKind.DROP_DOWN_LIST -> {
                    sb.append("<w:dropDownList>")
                    for (item in c.dropDownItems) {
                        sb.append("<w:listItem w:displayText=\"").append(esc(item))
                            .append("\" w:value=\"").append(esc(item)).append("\"/>")
                    }
                    sb.append("</w:dropDownList>")
                }
            }
            sb.append("</w:sdtPr><w:sdtContent><w:p><w:r><w:t>")
            val display = if (c.kind == ControlKind.CHECKBOX) (if (c.checked) "\u2612" else "\u2610") else c.currentValue
            sb.append(esc(display))
            sb.append("</w:t></w:r></w:p></w:sdtContent></w:sdt>")
        }
        sb.append("</w:body>")
        return sb.toString()
    }

    fun extractControls(xml: String): List<SdtControlSpec> {
        val root = BoundedXml.parse(xml.toByteArray(Charsets.UTF_8))
        val out = ArrayList<SdtControlSpec>()
        for (sdt in collectLocal(root, "sdt")) {
            val pr = sdt.children.firstOrNull { it.name.local == "sdtPr" } ?: continue
            val tag = pr.children.firstOrNull { it.name.local == "tag" }?.let { localAttr(it, "val") } ?: continue
            val alias = pr.children.firstOrNull { it.name.local == "alias" }?.let { localAttr(it, "val") }.orEmpty()
            val dd = pr.children.firstOrNull { it.name.local == "dropDownList" }
            val cb = pr.children.firstOrNull { it.name.local == "checkbox" }
            val dt = pr.children.firstOrNull { it.name.local == "date" }
            val kind = when {
                dd != null -> ControlKind.DROP_DOWN_LIST
                cb != null -> ControlKind.CHECKBOX
                dt != null -> ControlKind.DATE_PICKER
                else -> ControlKind.PLAIN_TEXT
            }
            val items = dd?.children?.filter { it.name.local == "listItem" }?.mapNotNull { localAttr(it, "value") }.orEmpty()
            val checked = cb?.children?.firstOrNull { it.name.local == "checked" }?.let { localAttr(it, "val") } == "1"
            val textVal = sdt.children.firstOrNull { it.name.local == "sdtContent" }
                ?.let { collectLocal(it, "t").joinToString("") { tNode -> tNode.text } }.orEmpty()
            out.add(SdtControlSpec(tag, alias, kind, textVal, items, checked))
        }
        return out
    }

    private fun collectLocal(node: BoundedXml.Node, local: String): List<BoundedXml.Node> {
        val acc = ArrayList<BoundedXml.Node>()
        fun walk(n: BoundedXml.Node) {
            if (n.name.local == local) acc.add(n)
            for (c in n.children) walk(c)
        }
        walk(node)
        return acc
    }

    private fun localAttr(node: BoundedXml.Node, local: String): String? =
        node.attributes.entries.firstOrNull { it.key.local == local }?.value

    fun applyBindings(controls: List<SdtControlSpec>, bindings: Map<String, String>): List<SdtControlSpec> =
        controls.map { c ->
            val v = bindings[c.tag] ?: return@map c
            when (c.kind) {
                ControlKind.CHECKBOX -> {
                    val b = v.equals("true", ignoreCase = true) || v == "1"
                    c.copy(checked = b, currentValue = if (b) "\u2612" else "\u2610")
                }
                ControlKind.DROP_DOWN_LIST -> {
                    require(c.dropDownItems.isEmpty() || v in c.dropDownItems) {
                        "Bound value '$v' is not in dropDownItems for '${c.tag}'"
                    }
                    c.copy(currentValue = v)
                }
                else -> c.copy(currentValue = v)
            }
        }

    private fun esc(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
