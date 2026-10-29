package dev.fusion.continuity

import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import android.view.InputDevice

data class Peripheral(val name: String, val detail: String)

/**
 * Peripheral inventory using permission-free APIs only.
 *
 * InputDevice tells us whether a real keyboard / mouse / trackpad is attached,
 * which is what a desktop shell actually needs. Bluetooth pairing lists are
 * deliberately NOT read: that needs BLUETOOTH_CONNECT and we do not request it.
 */
object Peripherals {

    fun inputDevices(): List<Peripheral> = InputDevice.getDeviceIds().asIterable().mapNotNull { id ->
        val device = InputDevice.getDevice(id) ?: return@mapNotNull null
        if (device.isVirtual) return@mapNotNull null
        val sources = device.sources
        val kinds = buildList {
            if (sources and InputDevice.SOURCE_KEYBOARD == InputDevice.SOURCE_KEYBOARD &&
                device.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC
            ) add("keyboard")
            if (sources and InputDevice.SOURCE_MOUSE == InputDevice.SOURCE_MOUSE) add("mouse")
            if (sources and InputDevice.SOURCE_TOUCHPAD == InputDevice.SOURCE_TOUCHPAD) add("touchpad")
            if (sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD) add("gamepad")
            if (sources and InputDevice.SOURCE_STYLUS == InputDevice.SOURCE_STYLUS) add("stylus")
        }
        if (kinds.isEmpty()) null
        else Peripheral(
            device.name,
            kinds.joinToString() + " · vendor ${device.vendorId} product ${device.productId}"
        )
    }

    fun usbDevices(context: Context): List<Peripheral> {
        val manager = context.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return emptyList()
        return runCatching {
            manager.deviceList.values.map { device ->
                Peripheral(
                    device.productName ?: device.deviceName,
                    "vendor ${device.vendorId} · product ${device.productId} · ${device.interfaceCount} interface(s)"
                )
            }
        }.getOrDefault(emptyList())
    }

    /** Hands content to the system share sheet. No custom transport, no network. */
    fun shareText(context: Context, text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(send, "Share from Fusion"))
    }

    /** R51 NTP-compensated equal-power crossfade audio route handoff planner. */
    fun planSafeAudioHandoff(
        sourcePositionMs: Long,
        t1ClientSendMs: Long,
        t2PeerRecvMs: Long,
        t3PeerSendMs: Long,
        t4ClientRecvMs: Long,
    ): AudioOutputHandoffCoordinator.HandoffPlan =
        AudioOutputHandoffCoordinator.planHandoff(sourcePositionMs, t1ClientSendMs, t2PeerRecvMs, t3PeerSendMs, t4ClientRecvMs)
}
