package dev.fusion.terminal

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * Honest local shell: built-in commands only. NOT a PTY, NOT root, NOT Linux userland.
 * Every command runs against public Android APIs and says what it cannot do.
 */
class LocalShell(private val context: Context) {
    fun run(line: String): List<String> {
        val parts = line.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (parts.isEmpty()) return emptyList()
        return when (parts[0].lowercase()) {
            "help" -> listOf("help ls-apps device mem echo clear", "PTY/Linux/SSH are NOT implemented in this build.")
            "echo" -> listOf(parts.drop(1).joinToString(" "))
            "device" -> listOf("sdk=${Build.VERSION.SDK_INT} soc=${Build.SOC_MODEL ?: Build.HARDWARE} fp=${Build.FINGERPRINT.take(48)}")
            "mem" -> {
                val am = context.getSystemService(android.app.ActivityManager::class.java)
                val mi = android.app.ActivityManager.MemoryInfo()
                am.getMemoryInfo(mi)
                listOf("totalMb=${mi.totalMem / 1048576} availMb=${mi.availMem / 1048576} low=${mi.lowMemory}")
            }
            "ls-apps" -> {
                val pm = context.packageManager
                val n = try {
                    pm.queryIntentActivities(
                        android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_LAUNCHER), 0
                    ).size
                } catch (_: Exception) { -1 }
                listOf("launchable=$n (names hidden here; use Apps window)")
            }
            "clear" -> listOf("__clear__")
            else -> listOf("${parts[0]}: not implemented. This is not a Unix shell.")
        }
    }
}
