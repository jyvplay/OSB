package dev.fusion.shell

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.*
import dev.fusion.media.SubRip
import dev.fusion.media.SubtitleInspector
import org.junit.Rule
import org.junit.Test

class SubtitleUiDeviceTest {
    @get:Rule val disposable = DisposableEmulatorRule()
    @get:Rule val compose = createComposeRule()
    @Test fun actualTextInputSelectsLiteralCueWithoutPretendingToPlay() {
        val subtitle = SubRip.parse("1\n00:00:01,000 --> 00:00:02,000\n<b>literal</b>".toByteArray())
        compose.setContent { MaterialTheme { androidx.compose.foundation.layout.Column { SubtitleInspector(subtitle) } } }
        compose.onNodeWithTag("subtitle-count").assertTextEquals("0 active cues; showing up to 8 (0 collision-free WebVTT lane(s))")
        compose.onNodeWithTag("subtitle-position").performTextReplacement("1000")
        compose.onNodeWithTag("subtitle-count").assertTextEquals("1 active cues; showing up to 8 (1 collision-free WebVTT lane(s))")
        compose.onNodeWithText("<b>literal</b>").assertIsDisplayed()
        compose.onNodeWithTag("subtitle-position").performTextReplacement("2000")
        compose.onNodeWithTag("subtitle-count").assertTextEquals("0 active cues; showing up to 8 (0 collision-free WebVTT lane(s))")
        compose.onNodeWithText("<b>literal</b>").assertDoesNotExist()
    }
}
