// SPDX-License-Identifier: Apache-2.0
package dev.fusion.shell

import dev.fusion.hw.UsbGuardPolicy
import dev.fusion.security.UsbPolicy

import android.Manifest
import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager

/**
 * R28 AUTHORED, NOT EXECUTED IN THIS SANDBOX. Requires Studio/CI compile plus
 * disposable-emulator verification before any detection claim.
 *
 * Honest scope: on USB attach/detach this receiver enumerates the device's
 * interface classes, evaluates them against [DEFAULT_RULES] with [UsbPolicy],
 * appends a bounded record to private storage, and posts a notification when
 * POST_NOTIFICATIONS is granted. It is DETECTION + USER ALERTING ONLY: a
 * non-root app cannot authorize or block USB hardware, descriptors can be
 * spoofed, and a passive inline logger is invisible to software. Real
 * enforcement belongs to the OS (Android 16+ USB Protection, GrapheneOS USB-C
 * port control); this receiver tells the user to review, nothing more.
 */
class UsbWatchReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != UsbManager.ACTION_USB_DEVICE_ATTACHED && action != UsbManager.ACTION_USB_DEVICE_DETACHED) return
        val device: UsbDevice = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java) ?: return
        val ifaces = (0 until device.interfaceCount)
            .mapNotNull { runCatching { device.getInterface(it) }.getOrNull() }
            .map { UsbPolicy.InterfaceClass(it.interfaceClass, it.interfaceSubclass, it.interfaceProtocol) }
            .toSet()
        if (ifaces.isEmpty() || ifaces.size > 32) return
        val policyDevice = UsbPolicy.Device(device.vendorId, device.productId, ifaces,
            (device.productName ?: device.deviceName ?: "usb").take(128).filterNot { it.isISOControl() })
        // R29: previously consented vid:pid pairs (private allowlist) feed the
        // combo rule's already-allowed condition, so a known device's
        // keyboard+mouse shape no longer re-flags. Unknown devices still
        // evaluate against an empty list: flagged for review, conservative by
        // design. Either way the event is recorded and still notifies.
        val known = runCatching {
            UsbAllowlist.open(context).contains(policyDevice.vid, policyDevice.pid)
        }.getOrDefault(false)
        val verdict = UsbPolicy.evaluate(
            DEFAULT_RULES, policyDevice, if (known) listOf(policyDevice) else emptyList())
        val locked = runCatching {
            context.getSystemService(KeyguardManager::class.java)?.isDeviceLocked == true
        }.getOrDefault(false)
        val guardCandidate = UsbGuardPolicy.UsbCandidate(
            vendorId = policyDevice.vid,
            productId = policyDevice.pid,
            serialSha256Hex = null,
            interfaces = ifaces.map { UsbGuardPolicy.UsbInterfaceSpec(it.cls, it.sub, it.proto) },
        )
        val guardVerdict = UsbGuardPolicy.evaluate(
            guardCandidate,
            UsbGuardPolicy.HostContext(
                screenLocked = locked,
                activeKeyboardCount = if (known) 0 else 1,
                lastKeyboardDisconnectMs = null,
                nowMs = System.currentTimeMillis(),
            ),
        )
        val attached = action == UsbManager.ACTION_USB_DEVICE_ATTACHED
        val summary = "USB ${if (attached) "attached" else "detached"}: %04x:%04x %s -> %s / guard=%s:%s (%s; NOT blocked)".format(
            policyDevice.vid, policyDevice.pid,
            ifaces.sortedBy { it.toString() }.joinToString(","),
            verdict.target.name,
            guardVerdict.action.name,
            guardVerdict.ruleId,
            if (known) "allowlisted, still recorded" else "review")
        appendRecord(context, summary)
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            notify(context, summary, attached)
        }
    }

    private fun appendRecord(context: Context, summary: String) {
        runCatching {
            val file = java.io.File(context.filesDir, "usb-watch.jsonl")
            val prior = if (file.isFile) file.readLines().takeLast(MAX_RECORDS - 1) else emptyList()
            val line = "{\"t\":${System.currentTimeMillis()},\"m\":\"" +
                summary.replace("\\", "\\\\").replace("\"", "\\\"").take(512) + "\"}"
            file.writeText((prior + line).joinToString("\n"))
        }
    }

    private fun notify(context: Context, summary: String, attached: Boolean) {
        runCatching {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "USB watch", NotificationManager.IMPORTANCE_HIGH))
            val note = Notification.Builder(context, CHANNEL)
                .setContentTitle(if (attached) "USB device attached - review" else "USB device detached")
                .setContentText(summary.take(256))
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setAutoCancel(true)
                .build()
            manager.notify(NOTIFICATION_ID, note)
        }
    }

    companion object {
        const val CHANNEL = "usb-watch"
        const val NOTIFICATION_ID = 4001
        const val MAX_RECORDS = 64

        /**
         * Advisory ruleset (verdict text only, never enforced): a keyboard+mouse
         * boot combo on one device is the classic injected-input shape and is
         * flagged; mass-storage-only is noted as lower risk; everything else
         * hits default-deny and is flagged for review.
         */
        val DEFAULT_RULES: List<UsbPolicy.Rule> by lazy {
            UsbPolicy.parse(
                "block id *:* with-interface { 03:01:01 03:01:02 } " +
                    "if !allowed-matches(with-interface one-of { 03:01:01 03:01:02 })\n" +
                    "allow id *:* with-interface { 08:06:50 }\n" +
                    "block id *:*\n")
        }
    }
}
