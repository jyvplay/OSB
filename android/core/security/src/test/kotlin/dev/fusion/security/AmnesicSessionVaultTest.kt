// SPDX-License-Identifier: Apache-2.0
package dev.fusion.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class AmnesicSessionVaultTest {
    @Test
    fun enforcesBoundedRamZeroizesOnWipeAndEnforcesTorSocks5hProxyPolicy() {
        val vault = AmnesicSessionVault()
        val secret = "SUPER_SECRET_SESSION_KEY_2026".toByteArray(StandardCharsets.UTF_8)
        val e1 = vault.putSecret("tor-auth", secret, wipeCallerArray = true)
        assertTrue(secret.all { it == 0.toByte() })
        assertFalse(vault.containsRawSubsequence("SUPER_SECRET".toByteArray(StandardCharsets.UTF_8)))
        assertEquals("SUPER_SECRET_SESSION_KEY_2026", String(vault.readSecret("tor-auth", e1), StandardCharsets.UTF_8))

        val e2 = vault.panicWipe()
        assertEquals(e1 + 1, e2)
        assertEquals(0, vault.activeSlotCount)

        assertFalse(TorTransportPolicy.evaluate("http://example.org", "socks5h://127.0.0.1:9050").admitted)
        assertTrue(TorTransportPolicy.evaluate("https://example.org", "socks5h://127.0.0.1:9050").admitted)
    }
}
