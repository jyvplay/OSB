package dev.fusion.shell

import android.app.ActivityManager
import android.app.role.RoleManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.media.MediaCodecList
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.fusion.capabilities.CapabilityProbe
import dev.fusion.hw.HwProbe
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real runtime inventories, not codec initialization or physical capability/performance acceptance.
 * Held against direct Android queries on a disposable emulator with no hotplug during observation.
 */
@RunWith(AndroidJUnit4::class)
class RuntimeInventoryDeviceTest {
    @get:Rule val disposable = DisposableEmulatorRule()
    @Test fun capabilityReportMatchesRuntimeWithoutInventingNpuDesktopOrDisplayOut() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val report = CapabilityProbe.inspect(context)
        assertTrue("Probe failures: ${report.errors}", report.errors.isEmpty())
        assertEquals(Build.VERSION.SDK_INT, report.sdk)
        assertEquals(Build.FINGERPRINT, report.fingerprint)
        assertEquals("${Build.SOC_MANUFACTURER} / ${Build.SOC_MODEL}", report.soc)
        val memory = ActivityManager.MemoryInfo()
        val am = context.getSystemService(ActivityManager::class.java)
        am.getMemoryInfo(memory)
        assertNotNull(report.ramGb)
        assertEquals(memory.totalMem / (1024.0 * 1024 * 1024), requireNotNull(report.ramGb), 0.000001)
        assertTrue(requireNotNull(report.ramGb) > 0)
        assertEquals(context.getSystemService(DisplayManager::class.java).getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION).size, report.presentationDisplays)
        assertEquals(context.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_HOME), report.homeRoleHeld)
        assertEquals(context.packageManager.hasSystemFeature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT), report.freeformDeclared)
        val declared = sortedSetOf<String>()
        for (codec in MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos) {
            if (codec.isEncoder && codec.isHardwareAccelerated) for (mime in codec.supportedTypes) if (mime.startsWith("video/")) declared.add(mime)
        }
        assertEquals(declared.toList(), report.videoEncoders)
        assertNull(report.displayOutSupported); assertNull(report.desktopSessionActive)
        assertEquals("Unknown; no NPU inference backend admitted", report.npuStatus)
        assertEquals(if (am.isLowRamDevice) "Android low-RAM device (Compat suggested, no hard block)" else "Unclassified; no device benchmark has been run", report.deviceClass)
    }
    @Test fun hardwareInventoryReflectsCodecDeclarationsNotAssumedWorkingReceivers() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val report = HwProbe.inspect(context)
        assertTrue("Probe failures: ${report.errors}", report.errors.isEmpty())
        val enc = mutableSetOf<Pair<String, Boolean>>(); val dec = mutableSetOf<Pair<String, Boolean>>()
        for (codec in MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos) for (mime in codec.supportedTypes) {
            if (mime.startsWith("video/", true)) (if (codec.isEncoder) enc else dec).add(mime to codec.isHardwareAccelerated)
        }
        // This emulator must actually expose video decoders: an empty/always-unknown report cannot pass.
        assertTrue(dec.isNotEmpty())
        assertEquals(enc, report.encoders.map { it.mime to it.hardwareAccelerated }.toSet())
        assertEquals(dec, report.decoders.map { it.mime to it.hardwareAccelerated }.toSet())
        assertEquals(enc.size, report.encoders.size); assertEquals(dec.size, report.decoders.size)
        assertTrue(report.encoders.all { it.encoder }); assertTrue(report.decoders.none { it.encoder })
        assertEquals(context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUDIO_LOW_LATENCY), report.lowLatencyAudio)
        val preferred = when {
            enc.any { it.first.equals("video/hevc", true) && it.second } -> "HEVC"
            enc.any { it.first.equals("video/avc", true) && it.second } -> "AVC"
            else -> null
        }
        assertEquals(preferred, report.preferredCastCodec)
    }
}
