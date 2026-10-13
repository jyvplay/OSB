package dev.fusion.perf

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager

/** Rolling frame-time stats against the 8.3ms @120Hz budget. No claims until measured. */
class FrameBudget(private val cap: Int = 240) {
    private val q = ArrayDeque<Float>()
    fun add(ms: Float) { q.addLast(ms); if (q.size > cap) q.removeFirst() }
    fun p95(): Float {
        if (q.isEmpty()) return 0f
        val s = q.sorted()
        return s[(s.size * 0.95).toInt().coerceAtMost(s.size - 1)]
    }
    fun jank(budget: Float = 8.3f): Float = if (q.isEmpty()) 0f else q.count { it > budget }.toFloat() / q.size
    fun size(): Int = q.size
}

enum class ThermalState { COOL, WARM, HOT, THROTTLED, CRITICAL, UNKNOWN }

object ThermalProbe {
    fun current(context: Context): ThermalState {
        return try {
            val pm = context.getSystemService(PowerManager::class.java)
            @Suppress("DEPRECATION")
            when (pm.currentThermalStatus) {
                PowerManager.THERMAL_STATUS_NONE,
                PowerManager.THERMAL_STATUS_LIGHT -> ThermalState.COOL
                PowerManager.THERMAL_STATUS_MODERATE -> ThermalState.WARM
                PowerManager.THERMAL_STATUS_SEVERE -> ThermalState.HOT
                PowerManager.THERMAL_STATUS_CRITICAL -> ThermalState.THROTTLED
                PowerManager.THERMAL_STATUS_EMERGENCY -> ThermalState.CRITICAL
                PowerManager.THERMAL_STATUS_SHUTDOWN -> ThermalState.CRITICAL
                else -> ThermalState.UNKNOWN
            }
        } catch (_: Exception) { ThermalState.UNKNOWN }
    }
}

object MemoryProbe {
    /** Private dirty PSS in MB for this process, or -1 when unavailable. */
    fun pssMb(context: Context): Long {
        return try {
            val am = context.getSystemService(ActivityManager::class.java)
            val pid = android.os.Process.myPid()
            val info = am.getProcessMemoryInfo(intArrayOf(pid)).firstOrNull() ?: return -1
            info.totalPrivateDirty / 1024L
        } catch (_: Exception) { -1 }
    }
}
