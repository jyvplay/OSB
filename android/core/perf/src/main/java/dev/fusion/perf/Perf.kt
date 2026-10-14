package dev.fusion.perf

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager

object ThermalProbe {
    fun current(context: Context): ThermalState {
        return try {
            val pm = context.getSystemService(PowerManager::class.java)
            ThermalPolicy.fromPlatform(pm.currentThermalStatus)
        } catch (_: Exception) { ThermalState.UNKNOWN }
    }
}

object MemoryProbe {
    /** Total proportional set size in MB for this process, or -1 when unavailable. */
    fun pssMb(context: Context): Long {
        return try {
            val am = context.getSystemService(ActivityManager::class.java)
            val pid = android.os.Process.myPid()
            val info = am.getProcessMemoryInfo(intArrayOf(pid)).firstOrNull() ?: return -1
            info.totalPss / 1024L
        } catch (_: Exception) { -1 }
    }
}
