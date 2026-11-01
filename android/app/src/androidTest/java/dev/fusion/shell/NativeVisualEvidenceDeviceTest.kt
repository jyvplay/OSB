package dev.fusion.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class NativeVisualEvidenceDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun dialogCaptureFilenameAllowlistRejectsTraversalAndUnknownPrefixes() {
        listOf("r24c-structured", "r58-text-grid", "r58-service-review", "r59-typed-grid").forEach {
            assertTrue("Expected safe dialog name: $it", NativeCaptureNames.dialog(it))
        }
        listOf("r58-../outside", "../r58-text-grid", "r60-text-grid", "r58-",  "r58-" + "a".repeat(41), "r58-A").forEach {
            assertFalse("Expected filename refusal: $it", NativeCaptureNames.dialog(it))
        }
    }

    @Test fun minimumTargetCheckerAcceptsFortyEightDpAndRejectsThirtyTwoDp() {
        compose.setContent {
            Column {
                Box(Modifier.size(48.dp).testTag("target-good")) { }
                Box(Modifier.size(32.dp).testTag("target-small")) { }
            }
        }
        NativeVisualEvidence.assertMinimumTarget(compose, "target-good")
        val error = runCatching { NativeVisualEvidence.assertMinimumTarget(compose, "target-small") }.exceptionOrNull()
        assertTrue("A 32dp target must fail the geometry check, got $error", error is AssertionError)
        val detail = error?.message.orEmpty()
        assertTrue("Failure must include measured dimensions", detail.contains("target-small") && detail.contains("32"))
    }

    @Test fun textFitCheckerAcceptsVisibleTextAndRejectsClippedScrollableText() {
        compose.setContent { EvidenceFixture() }
        NativeVisualEvidence.assertFullTextVisible(compose, "fits")
        val error = runCatching { NativeVisualEvidence.assertFullTextVisible(compose, "clipped") }.exceptionOrNull()
        assertTrue("A clipped disclosure must fail the geometry check, got $error", error is AssertionError)
        assertTrue("Failure must identify the clipped text", error?.message?.contains("clipped") == true)
    }
}

@Composable
private fun EvidenceFixture() {
    Column(Modifier.width(180.dp)) {
        Text("A short visible disclosure.", Modifier.testTag("fits"))
        Text(
            "A deliberately long warning that must exceed this viewport. ".repeat(8),
            Modifier.heightIn(max = 32.dp).verticalScroll(rememberScrollState()).testTag("clipped"),
        )
    }
}
