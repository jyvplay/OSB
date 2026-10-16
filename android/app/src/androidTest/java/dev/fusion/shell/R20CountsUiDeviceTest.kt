package dev.fusion.shell
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import dev.fusion.automation.TaskRepository
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.io.File
import java.util.UUID
import java.util.regex.Pattern
class R20CountsUiDeviceTest {
    private val compose=createAndroidComposeRule<MainActivity>()
    @get:Rule val rules:RuleChain=RuleChain.outerRule(DisposableEmulatorRule()).around(compose)
    @Test fun actualCountsPreviewAndSystemSaveDoNotMutatePrivateTasks() {
        val c=InstrumentationRegistry.getInstrumentation().targetContext
        val store=File(c.noBackupFilesDir,"tasks-v1.bin")
        TaskRepository(store).add(0,"Not disclosed in counts",java.time.LocalDate.of(2028,2,29));val before=store.readBytes()
        compose.waitUntil(10000) {compose.onAllNodes(hasTestTag("shelf-automation") and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("shelf-automation").performScrollTo().performClick()
        compose.onNodeWithText("Enable for this session with test data").performScrollTo().performClick()
        compose.onNodeWithTag("tasks-mode").performScrollTo().performClick()
        compose.waitUntil(10000) {compose.onAllNodes(hasTestTag("tasks-preview-counts") and isEnabled()).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("tasks-preview-counts").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("Counts: active open 1, active completed 0; archived open 0, archived completed 0.").assertExists()
        assertArrayEquals(before,store.readBytes())
        compose.onNodeWithTag("tasks-export-counts").performScrollTo().assertIsDisplayed().performClick()
        val device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        assertTrue(device.wait(Until.hasObject(By.pkg(Pattern.compile(".*documentsui"))),10000))
        val name=device.wait(Until.findObject(By.clazz("android.widget.EditText")),10000);assertNotNull(name)
        name.text="r20-counts-${UUID.randomUUID()}.xlsx"
        val save=device.wait(Until.findObject(By.text(Pattern.compile("(?i)save"))),10000);assertNotNull(save);save.click()
        assertTrue(device.wait(Until.gone(By.pkg(Pattern.compile(".*documentsui"))),10000))
        assertEquals(c.packageName,device.currentPackageName)
        compose.waitUntil(10000) {compose.onAllNodesWithText("Counts-only XLSX readback verified:",substring=true).fetchSemanticsNodes().isNotEmpty()}
        assertArrayEquals(before,store.readBytes())
        // Unique report stays only on the explicitly disposable AVD; no guessed-URI deletion.
    }
}
