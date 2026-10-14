package dev.fusion.documents

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.DataOutputStream

/** PR-01, FF-15, FM-18. Deterministic stream/fault unit tests, not SAF device evidence. */
class DocumentSafetyTest {
    private fun document(content: String = "source") = TextDocument("doc-1", "test.txt", "content://source/1", content, TextBytes.digest("source"))
    private fun roundTrip(s: EditorSession): EditorSession = ByteArrayOutputStream().also { SessionCodec.write(s, it) }
        .let { SessionCodec.read(ByteArrayInputStream(it.toByteArray())) }

    @Test fun utf8PreservesBomNewlinesAndSupplementaryCharacters() {
        val text = "\uFEFFa\r\n猫 😀\n"
        assertEquals(text, TextBytes.decode(TextBytes.encode(text)))
    }
    @Test fun malformedUtf8IsRejectedInsteadOfSilentlyReplacingBytes() {
        assertThrows(Exception::class.java) { TextBytes.decode(byteArrayOf(0xC3.toByte(), 0x28)) }
        assertThrows(Exception::class.java) { TextBytes.encode("\uD800") }
    }
    @Test fun byteLimitAppliesToUtf8NotJustCharacterCount() {
        assertEquals(TextBytes.MAX_BYTES, TextBytes.encode("a".repeat(TextBytes.MAX_BYTES)).size)
        assertThrows(IllegalArgumentException::class.java) { TextBytes.encode("猫".repeat(TextBytes.MAX_BYTES / 2)) }
        assertThrows(IllegalArgumentException::class.java) { TextBytes.read(ByteArrayInputStream(ByteArray(TextBytes.MAX_BYTES + 1))) }
    }
    @Test fun savedSnapshotDoesNotMarkLaterEditsClean() {
        val before = document().copy(content = "at picker")
        val after = before.copy(content = "edited while picker open").saved(before.content)
        assertTrue(after.dirty)
        assertFalse(after.copy(content = before.content).dirty)
        assertEquals("content://source/1", after.sourceUri)
    }
    @Test fun sessionsAndPendingPickerPayloadSurviveCodecRoundTrip() {
        val d = document("unsaved")
        val state = EditorSession(listOf(d), d.id, PendingCopy(d.id, d.name, d.sourceUri, "older snapshot"))
        assertEquals(state, roundTrip(state))
        assertEquals(EditorSession(), roundTrip(EditorSession()))
    }
    @Test fun corruptTruncatedAndFutureRecoveryCannotBeAccepted() {
        val bytes = ByteArrayOutputStream().also { SessionCodec.write(EditorSession(listOf(document()), "doc-1"), it) }.toByteArray()
        assertThrows(Exception::class.java) { SessionCodec.read(ByteArrayInputStream(bytes.copyOf(bytes.size - 3))) }
        bytes[15] = (bytes[15].toInt() xor 1).toByte()
        assertThrows(Exception::class.java) { SessionCodec.read(ByteArrayInputStream(bytes)) }
        assertThrows(Exception::class.java) { SessionCodec.read(ByteArrayInputStream(byteArrayOf(127,127,127,127))) }
        val futurePayload = ByteArrayOutputStream().also {
            DataOutputStream(it).apply { writeInt(0x46534544); writeInt(2) }
        }.toByteArray()
        val future = ByteArrayOutputStream().also {
            DataOutputStream(it).apply {
                writeInt(futurePayload.size); write(futurePayload); writeUTF(TextBytes.digest(futurePayload))
            }
        }.toByteArray()
        assertThrows(IllegalArgumentException::class.java) { SessionCodec.read(ByteArrayInputStream(future)) }
    }
    @Test fun invalidIdentitiesAndDanglingSelectionsAreRejected() {
        assertThrows(Exception::class.java) { roundTrip(EditorSession(listOf(document(), document()), "doc-1")) }
        assertThrows(Exception::class.java) { roundTrip(EditorSession(listOf(document()), "missing")) }
        assertThrows(Exception::class.java) { roundTrip(EditorSession(listOf(document()), "doc-1", PendingCopy("missing", "x", null, ""))) }
    }
    @Test fun verifyRequiresClosedOutputAndExactReadbackIncludingEmptyText() {
        for (data in listOf(byteArrayOf(), TextBytes.encode("猫\r\n"))) {
            var closed = false
            val out = object : ByteArrayOutputStream() {
                override fun close() {
                    closed = true
                    super.close()
                }
            }
            VerifiedCopy.write(data, { out }, { assertTrue(closed); ByteArrayInputStream(out.toByteArray()) })
            assertArrayEquals(data, out.toByteArray())
        }
    }
    @Test fun providerTruncationAppendedDataAndUnreadableCopiesAreFailures() {
        val wanted = TextBytes.encode("expected")
        for (actual in listOf(byteArrayOf(), TextBytes.encode("expecte"), TextBytes.encode("expected-extra"))) {
            assertThrows(IllegalStateException::class.java) {
                VerifiedCopy.write(wanted, { ByteArrayOutputStream() }, { ByteArrayInputStream(actual) })
            }
        }
        assertThrows(IOException::class.java) {
            VerifiedCopy.write(wanted, { ByteArrayOutputStream() }, { throw IOException("grant revoked") })
        }
    }
    @Test fun writeFailureDoesNotInvokeVerification() {
        var reopened = false
        assertThrows(IOException::class.java) {
            VerifiedCopy.write(byteArrayOf(1), { throw IOException("full") }, { reopened = true; ByteArrayInputStream(byteArrayOf()) })
        }
        assertFalse(reopened)
    }
}
