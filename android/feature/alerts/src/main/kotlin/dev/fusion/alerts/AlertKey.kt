package dev.fusion.alerts

/**
 * Alert deduplication keys (stated contract): key = lowercase(category)
 * + '|' + lowercase(source) + '|' + time bucket (epoch floored to
 * [bucketS]); two alerts with equal keys are one alert for throttling
 * purposes. The pipe is the separator, so pipes in inputs REFUSE
 * (escaping would make keys ambiguous across versions — refusal keeps
 * every key canonical forever).
 *
 * Declared narrowings: composition only — AlertThrottle owns the
 * rate decisions these keys feed; bucket arithmetic matches the
 * privacy TimeCoarse floor law (re-stated here, independently coded).
 */
object AlertKey {
    const val SCOPE = "Canonical category|source|bucket keys; pipes refuse (no escaping = keys stable forever)."

    fun key(category: String, source: String, epochS: Long, bucketS: Long): String {
        require(category.isNotBlank() && source.isNotBlank()) { "blank key field" }
        require('|' !in category && '|' !in source) { "pipe in key field refused (keys must stay canonical)" }
        require(bucketS > 0 && epochS >= 0) { "bad bucket/epoch" }
        val bucket = epochS / bucketS
        return category.lowercase() + "|" + source.lowercase() + "|" + bucket
    }

    fun sameAlert(k1: String, k2: String): Boolean = k1 == k2

    fun parts(key: String): Triple<String, String, Long> {
        val p = key.split('|')
        require(p.size == 3) { "not an alert key" }
        return Triple(p[0], p[1], p[2].toLongOrNull() ?: throw IllegalArgumentException("bucket not numeric"))
    }
}
