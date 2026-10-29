// SPDX-License-Identifier: Apache-2.0
package dev.fusion.format.pdf

/**
 * ISO 32000-2 §8.11 Optional Content Groups (**OCG** / PDF Layers) `/OCProperties` catalog
 * dictionary + `/BDC ... EMC` marked-content layer stream generator (e.g., toggleable Annotations,
 * Watermark, and Print-Only layers).
 */
object PdfOptionalContentLayersWriter {
    const val SCOPE =
        "ISO 32000-2 §8.11 PDF Optional Content Groups (/OCG, /OCProperties, /D /ON /OFF, and /OC /Layer BDC..EMC) writer."

    data class PdfLayerSpec(
        val idTag: String,
        val displayName: String,
        val visibleByDefault: Boolean = true,
        val objNumber: Int,
    )

    fun buildOcPropertiesCatalogFragment(layers: List<PdfLayerSpec>): String {
        require(layers.isNotEmpty() && layers.map { it.objNumber }.distinct().size == layers.size)
        val allRefs = layers.joinToString(" ") { "${it.objNumber} 0 R" }
        val onRefs = layers.filter { it.visibleByDefault }.joinToString(" ") { "${it.objNumber} 0 R" }
        val offRefs = layers.filter { !it.visibleByDefault }.joinToString(" ") { "${it.objNumber} 0 R" }
        return "/OCProperties << /OCGs [$allRefs] /D << /ON [$onRefs] /OFF [$offRefs] /Order [$allRefs] >> >>"
    }

    fun buildOcgObjects(layers: List<PdfLayerSpec>): String =
        layers.joinToString("\n") { l ->
            val safe = l.displayName.replace("(", "\\(").replace(")", "\\)")
            "${l.objNumber} 0 obj\n<< /Type /OCG /Name ($safe) >>\nendobj"
        }

    fun wrapLayerContentStream(layerTag: String, drawingOps: String): String {
        require(Regex("^[A-Za-z0-9_]+$").matches(layerTag))
        return "/OC /$layerTag BDC\n${drawingOps.trim()}\nEMC\n"
    }
}
