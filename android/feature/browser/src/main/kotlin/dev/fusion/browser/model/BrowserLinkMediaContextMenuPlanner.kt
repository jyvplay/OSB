// SPDX-License-Identifier: Apache-2.0
package dev.fusion.browser.model

/**
 * **Right-Sized Browser Link / Image / Video / Music Context-Menu & 6-Method `Recall` Submenu Planner**
 * ensuring every browser secondary-click menu stays compact (`<= 280 dp` primary, `<= 240 dp`
 * `"Save via Recall ▸"` submenu) and offers direct method selection (`T1`..`T5`) when appropriate.
 */
object BrowserLinkMediaContextMenuPlanner {
    const val SCOPE =
        "Right-sized Browser link/image/video/music right-click menu and 6-method 'Save via Recall' cascading submenu planner."

    enum class HitTargetKind { PAGE_BACKGROUND, HYPERLINK, IMAGE_ELEMENT, VIDEO_ELEMENT, AUDIO_ELEMENT }

    data class BrowserMenuItem(
        val id: String,
        val label: String,
        val shortcut: String = "",
        val submenu: List<BrowserMenuItem> = emptyList(),
    )

    fun buildMenu(target: HitTargetKind, incognitoActive: Boolean): List<BrowserMenuItem> {
        val recallSubmenu = if (incognitoActive) {
            emptyList() // Recall auto-recording strictly disabled/refused in Incognito!
        } else {
            listOf(
                BrowserMenuItem("recall_auto", "Auto-Select Best Tier"),
                BrowserMenuItem("recall_t1", "Tier 1: Direct / HTTP Range"),
                BrowserMenuItem("recall_t2", "Tier 2: HLS / DASH -> fMP4"),
                BrowserMenuItem("recall_t25", "Tier 2.5: WebRTC RTP Opus/VP8"),
                BrowserMenuItem("recall_t4", "Tier 4: Viewport AV Capture"),
                BrowserMenuItem("recall_t5", "Tier 5: ISO 28500 WARC/1.1"),
            )
        }

        return buildList {
            when (target) {
                HitTargetKind.HYPERLINK -> {
                    add(BrowserMenuItem("open_new_tab", "Open Link in New Tab", "Ctrl+Click"))
                    add(BrowserMenuItem("copy_clean_link", "Copy Clean Link (No UTM)"))
                }
                HitTargetKind.IMAGE_ELEMENT -> {
                    add(BrowserMenuItem("open_image", "Open Image in New Tab"))
                    add(BrowserMenuItem("copy_image", "Copy Image"))
                }
                HitTargetKind.VIDEO_ELEMENT, HitTargetKind.AUDIO_ELEMENT -> {
                    add(BrowserMenuItem("pip_toggle", "Picture-in-Picture"))
                    add(BrowserMenuItem("mute_tab", "Mute Tab Audio", "Ctrl+M"))
                }
                HitTargetKind.PAGE_BACKGROUND -> {
                    add(BrowserMenuItem("back", "Back", "Alt+Left"))
                    add(BrowserMenuItem("reload", "Reload", "Ctrl+R"))
                    add(BrowserMenuItem("save_mhtml", "Save Page Snapshot (.mhtml)"))
                }
            }
            if (recallSubmenu.isNotEmpty()) {
                add(BrowserMenuItem("recall_cascade", "Save via Recall", submenu = recallSubmenu))
            }
            add(BrowserMenuItem("inspect_dom", "Inspect Element", "F12"))
        }
    }
}
