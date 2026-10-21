// SPDX-License-Identifier: Apache-2.0
package dev.fusion.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedStreamContractTest {
    @Test
    fun enforcesOfficialPrivacyEnhancedEmbedsAndHostAllowlist() {
        val yt = EmbeddedStreamContract.toOfficialEmbed(ServiceLink.parse("https://youtu.be/abcdefghijk?si=track"))
        assertTrue(yt.embedUrl.startsWith("https://www.youtube-nocookie.com/embed/abcdefghijk"))
        assertTrue(EmbeddedStreamContract.isHostAllowedForEmbed(yt, "www.youtube-nocookie.com"))
        assertFalse(EmbeddedStreamContract.isHostAllowedForEmbed(yt, "evil-tracker.example"))
    }
}
