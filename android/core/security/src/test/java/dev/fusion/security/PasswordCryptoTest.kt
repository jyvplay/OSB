package dev.fusion.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordCryptoTest {

    private val secret = "notes + settings payload".toByteArray()

    @Test fun roundTripRecoversExactBytes() {
        val blob = PasswordCrypto.encrypt("correct horse".toCharArray(), secret)
        assertArrayEquals(secret, PasswordCrypto.decrypt("correct horse".toCharArray(), blob))
    }

    @Test fun wrongPasswordFailsClosed() {
        val blob = PasswordCrypto.encrypt("right".toCharArray(), secret)
        assertThrows(Exception::class.java) {
            PasswordCrypto.decrypt("wrong".toCharArray(), blob)
        }
    }

    @Test fun tamperedCiphertextIsRejected() {
        val blob = PasswordCrypto.encrypt("pw".toCharArray(), secret)
        blob[blob.size - 1] = (blob[blob.size - 1] + 1).toByte()
        assertThrows(Exception::class.java) { PasswordCrypto.decrypt("pw".toCharArray(), blob) }
    }

    @Test fun nonBackupFileGivesClearError() {
        val e = assertThrows(PasswordCrypto.BadContainer::class.java) {
            PasswordCrypto.decrypt("pw".toCharArray(), "hello world not a backup".toByteArray())
        }
        assertTrue(e.message!!.contains("Fusion backup"))
    }

    @Test fun saltAndIvAreRandomPerBackup() {
        val a = PasswordCrypto.encrypt("pw".toCharArray(), secret)
        val b = PasswordCrypto.encrypt("pw".toCharArray(), secret)
        assertEquals(a.size, b.size)
        assertNotEquals(a.toList(), b.toList())
    }
}
