package dev.fusion.perf

import kotlin.math.ceil

/** RG-01: platform status mapping, not a measured thermal-control implementation. */
enum class ThermalState { COOL, WARM, HOT, THROTTLED, CRITICAL, UNKNOWN }
object ThermalPolicy {
    // Android PowerManager THERMAL_STATUS_* numerical values, stable since API29.
    fun fromPlatform(status: Int): ThermalState = when (status) {
        0 -> ThermalState.COOL; 1 -> ThermalState.WARM; 2 -> ThermalState.HOT
        3 -> ThermalState.THROTTLED; 4, 5, 6 -> ThermalState.CRITICAL
        else -> ThermalState.UNKNOWN
    }
}
/** Null means unmeasured. Samples are explicit; this class does not collect frames by itself. */
class FrameBudget(private val cap: Int = 240) {
    init { require(cap > 0) }
    private val q = ArrayDeque<Float>()
    fun add(ms: Float) { require(ms.isFinite() && ms >= 0); q.addLast(ms); if (q.size > cap) q.removeFirst() }
    fun p95(): Float? = if (q.isEmpty()) null else q.sorted()[ceil(q.size * .95).toInt() - 1]
    fun jank(budget: Float = 8.3f): Float? {
        require(budget.isFinite() && budget > 0)
        return if (q.isEmpty()) null else q.count { it > budget }.toFloat() / q.size
    }
    fun size(): Int = q.size
}
