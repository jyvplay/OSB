// SPDX-License-Identifier: Apache-2.0
package dev.fusion.native

/**
 * Clean-room Save-As & Cross-Format Conversion Fidelity Loss Advisor (`FF-11`, `FF-16`).
 *
 * Computes exact document/spreadsheet/image features that will be downgraded or dropped when
 * saving from `sourceFormat` into `targetFormat` and requires explicit user acknowledgement
 * whenever `requiresUserConfirmation == true` (never silently strips tracked changes, formulas,
 * layers, or multi-sheet workbooks).
 */
object ExportFidelityAdvisor {
    enum class FeatureUsed {
        FORMULAS,
        DYNAMIC_ARRAYS_OR_LAMBDA,
        MULTIPLE_SHEETS,
        CHARTS_OR_SPARKLINES,
        TRACKED_CHANGES,
        COMMENTS_OR_FOOTNOTES,
        TABLES_AND_STYLES,
        EMBEDDED_FONTS,
        VECTOR_LAYERS,
        TRANSPARENCY_ALPHA,
        EXIF_METADATA,
        VBA_MACROS,
    }

    enum class LossSeverity { NONE, MINOR_LAYOUT_DRIFT, FEATURE_FLATTENED, DATA_DESTRUCTIVE }

    data class FeatureLossItem(
        val feature: FeatureUsed,
        val severity: LossSeverity,
        val explanation: String,
    )

    data class ConversionAdvisory(
        val sourceFormat: String,
        val targetFormat: String,
        val losses: List<FeatureLossItem>,
        val maxSeverity: LossSeverity,
        val requiresUserConfirmation: Boolean,
    )

    private val SUPPORTED_FEATURES: Map<String, Set<FeatureUsed>> = mapOf(
        "xlsx" to setOf(
            FeatureUsed.FORMULAS, FeatureUsed.DYNAMIC_ARRAYS_OR_LAMBDA, FeatureUsed.MULTIPLE_SHEETS,
            FeatureUsed.CHARTS_OR_SPARKLINES, FeatureUsed.COMMENTS_OR_FOOTNOTES, FeatureUsed.TABLES_AND_STYLES,
        ),
        "ods" to setOf(
            FeatureUsed.FORMULAS, FeatureUsed.MULTIPLE_SHEETS, FeatureUsed.COMMENTS_OR_FOOTNOTES,
            FeatureUsed.TABLES_AND_STYLES,
        ),
        "csv" to emptySet(),
        "docx" to setOf(
            FeatureUsed.TRACKED_CHANGES, FeatureUsed.COMMENTS_OR_FOOTNOTES, FeatureUsed.TABLES_AND_STYLES,
            FeatureUsed.EMBEDDED_FONTS,
        ),
        "odt" to setOf(
            FeatureUsed.TRACKED_CHANGES, FeatureUsed.COMMENTS_OR_FOOTNOTES, FeatureUsed.TABLES_AND_STYLES,
        ),
        "md" to setOf(FeatureUsed.TABLES_AND_STYLES),
        "txt" to emptySet(),
        "pdf" to setOf(FeatureUsed.TABLES_AND_STYLES, FeatureUsed.EMBEDDED_FONTS, FeatureUsed.VECTOR_LAYERS),
        "png" to setOf(FeatureUsed.TRANSPARENCY_ALPHA),
        "jpg" to setOf(FeatureUsed.EXIF_METADATA),
    )

    fun evaluateSaveAs(
        sourceFormat: String,
        targetFormat: String,
        activeFeatures: Set<FeatureUsed>,
    ): ConversionAdvisory {
        val src = sourceFormat.trim().lowercase().removePrefix(".")
        val dst = targetFormat.trim().lowercase().removePrefix(".")
        val targetCap = SUPPORTED_FEATURES[dst]
            ?: throw IllegalArgumentException("unknown export target format '$dst'")

        val losses = ArrayList<FeatureLossItem>()
        for (f in activeFeatures.sortedBy { it.ordinal }) {
            if (f == FeatureUsed.VBA_MACROS) {
                losses.add(
                    FeatureLossItem(
                        f,
                        LossSeverity.FEATURE_FLATTENED,
                        "VBA macros are disabled and stripped on clean-room export (FF-17).",
                    ),
                )
                continue
            }
            if (f !in targetCap) {
                val (sev, msg) = when (f) {
                    FeatureUsed.MULTIPLE_SHEETS ->
                        LossSeverity.DATA_DESTRUCTIVE to "Target '.$dst' stores only the active sheet; non-active sheets will be omitted."
                    FeatureUsed.FORMULAS, FeatureUsed.DYNAMIC_ARRAYS_OR_LAMBDA ->
                        LossSeverity.FEATURE_FLATTENED to "Formulas and LAMBDA/spill expressions flatten to static cached values in '.$dst'."
                    FeatureUsed.TRACKED_CHANGES ->
                        LossSeverity.DATA_DESTRUCTIVE to "Tracked redline revisions cannot be represented in '.$dst' and would be flattened."
                    FeatureUsed.VECTOR_LAYERS ->
                        LossSeverity.FEATURE_FLATTENED to "Editable layers rasterize into a flat bitmap in '.$dst'."
                    FeatureUsed.TRANSPARENCY_ALPHA ->
                        LossSeverity.FEATURE_FLATTENED to "Alpha transparency composites onto a solid matte in '.$dst'."
                    FeatureUsed.EMBEDDED_FONTS ->
                        LossSeverity.MINOR_LAYOUT_DRIFT to "Embedded fonts are not bundled in '.$dst'; pagination may reflow."
                    else ->
                        LossSeverity.FEATURE_FLATTENED to "${f.name} is not preserved in '.$dst'."
                }
                losses.add(FeatureLossItem(f, sev, msg))
            }
        }

        val maxSev = losses.maxOfOrNull { it.severity } ?: LossSeverity.NONE
        return ConversionAdvisory(
            sourceFormat = src,
            targetFormat = dst,
            losses = losses,
            maxSeverity = maxSev,
            requiresUserConfirmation = maxSev >= LossSeverity.FEATURE_FLATTENED,
        )
    }
}
