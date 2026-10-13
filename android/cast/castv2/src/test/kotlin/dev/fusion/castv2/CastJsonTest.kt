package dev.fusion.castv2

import org.junit.Assert.assertTrue
import org.junit.Test

class CastJsonTest {
    @Test fun launchContainsReceiverAndRequestId() {
        val j = CastJson.launch(7)
        assertTrue(j.contains("CC1AD845"))
        assertTrue(j.contains("\"requestId\":7"))
    }
    @Test fun loadContainsUrlAndMime() {
        val j = CastJson.load(1, "https://example.com/video.mp4", "video/mp4", "Demo")
        assertTrue(j.contains("video/mp4"))
        assertTrue(j.contains("https://example.com/video.mp4"))
    }
}
