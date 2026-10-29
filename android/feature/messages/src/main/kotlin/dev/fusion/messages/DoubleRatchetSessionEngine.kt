package dev.fusion.messages

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Clean-room Double Ratchet (`X25519` DH Ratchet + `HKDF-SHA256` Root/Chain Ratchet +
 * `ChaCha20-Poly1305` AEAD) End-to-End Encrypted Session Engine (`MS-01`, Apache-2.0).
 *
 * Provides:
 * - Forward secrecy on every single message (`chainKey -> (nextChainKey, messageKey)` via
 *   `HMAC-SHA256(chainKey, 0x02)` / `0x01`, immediately zeroizing consumed `messageKey`),
 * - Post-compromise break-in recovery via fresh ephemeral `X25519` DH ratchet turns,
 * - Bounded out-of-order skipped message-key cache (`maxSkippedKeys = 64`) so dropped or
 *   reordered packets decrypt cleanly while preventing DoS memory exhaustion!
 */
object DoubleRatchetSessionEngine {
    const val SCOPE =
        "X25519 DH + HMAC/HKDF-SHA256 Double Ratchet E2EE session engine with forward-secure message keys & bounded out-of-order cache (MS-01)"

    const val MAX_SKIPPED_KEYS = 64

    data class RatchetHeader(
        val senderDhPublicEncoded: ByteArray,
        val previousChainLength: Int,
        val messageNumber: Int,
    ) {
        fun serializeAad(): ByteArray =
            ByteBuffer.allocate(8 + senderDhPublicEncoded.size)
                .putInt(previousChainLength)
                .putInt(messageNumber)
                .put(senderDhPublicEncoded)
                .array()
    }

    data class EncryptedRatchetMessage(
        val header: RatchetHeader,
        val ciphertext: ByteArray,
    )

    class RatchetParty private constructor(
        private var rootKey: ByteArray,
        private var dhSelf: KeyPair,
        private var dhRemotePub: PublicKey?,
        private var sendChainKey: ByteArray?,
        private var recvChainKey: ByteArray?,
    ) {
        private var sendCount = 0
        private var recvCount = 0
        private var prevSendCount = 0
        private val skippedKeys = LinkedHashMap<String, ByteArray>()

        fun encrypt(plaintext: ByteArray): EncryptedRatchetMessage {
            val ck = sendChainKey ?: error("Initiator/responder send chain not yet initialized")
            val (nextCk, msgKey) = kdfChain(ck)
            sendChainKey = nextCk
            val hdr = RatchetHeader(dhSelf.public.encoded, prevSendCount, sendCount++)
            val ct = aeadSeal(msgKey, hdr.serializeAad(), plaintext)
            msgKey.fill(0)
            return EncryptedRatchetMessage(hdr, ct)
        }

        fun decrypt(msg: EncryptedRatchetMessage): ByteArray {
            val skippedId = "${msg.header.senderDhPublicEncoded.contentHashCode()}:${msg.header.messageNumber}"
            val cachedMk = skippedKeys.remove(skippedId)
            if (cachedMk != null) {
                return try {
                    aeadOpen(cachedMk, msg.header.serializeAad(), msg.ciphertext)
                } finally {
                    cachedMk.fill(0)
                }
            }

            if (dhRemotePub == null || !dhRemotePub!!.encoded.contentEquals(msg.header.senderDhPublicEncoded)) {
                skipMessageKeysUntil(msg.header.previousChainLength)
                performDhRatchetStep(decodeX25519Pub(msg.header.senderDhPublicEncoded))
            }
            skipMessageKeysUntil(msg.header.messageNumber)
            val ck = recvChainKey ?: error("Receive chain not initialized")
            val (nextCk, msgKey) = kdfChain(ck)
            recvChainKey = nextCk
            recvCount++
            return try {
                aeadOpen(msgKey, msg.header.serializeAad(), msg.ciphertext)
            } finally {
                msgKey.fill(0)
            }
        }

        private fun skipMessageKeysUntil(untilMsgNum: Int) {
            val ck0 = recvChainKey ?: return
            require(untilMsgNum - recvCount <= MAX_SKIPPED_KEYS) {
                "Refusing excessive skipped ratchet jump (${untilMsgNum - recvCount} > $MAX_SKIPPED_KEYS)"
            }
            var ck = ck0
            val remoteHash = dhRemotePub!!.encoded.contentHashCode()
            while (recvCount < untilMsgNum) {
                val (nextCk, mk) = kdfChain(ck)
                ck = nextCk
                if (skippedKeys.size >= MAX_SKIPPED_KEYS) {
                    val eldest = skippedKeys.entries.first()
                    eldest.value.fill(0)
                    skippedKeys.remove(eldest.key)
                }
                skippedKeys["$remoteHash:$recvCount"] = mk
                recvCount++
            }
            recvChainKey = ck
        }

        private fun performDhRatchetStep(newRemote: PublicKey) {
            prevSendCount = sendCount
            sendCount = 0
            recvCount = 0
            dhRemotePub = newRemote
            val (rk1, rck) = kdfRoot(rootKey, x25519Shared(dhSelf.private, newRemote))
            recvChainKey = rck
            dhSelf = generateX25519KeyPair()
            val (rk2, sck) = kdfRoot(rk1, x25519Shared(dhSelf.private, newRemote))
            rootKey = rk2
            sendChainKey = sck
        }

        companion object {
            fun createLinkedPair(sharedSecret32: ByteArray): Pair<RatchetParty, RatchetParty> {
                require(sharedSecret32.size == 32)
                val bobKp = generateX25519KeyPair()
                val aliceKp = generateX25519KeyPair()
                val (aliceRk, aliceSendCk) = kdfRoot(sharedSecret32, x25519Shared(aliceKp.private, bobKp.public))
                val alice = RatchetParty(aliceRk, aliceKp, bobKp.public, aliceSendCk, null)
                val bob = RatchetParty(sharedSecret32.copyOf(), bobKp, null, null, null)
                return alice to bob
            }
        }
    }

    private fun generateX25519KeyPair(): KeyPair = KeyPairGenerator.getInstance("X25519").generateKeyPair()

    private fun decodeX25519Pub(x509Bytes: ByteArray): PublicKey =
        java.security.KeyFactory.getInstance("X25519").generatePublic(java.security.spec.X509EncodedKeySpec(x509Bytes))

    private fun x25519Shared(priv: PrivateKey, pub: PublicKey): ByteArray {
        val ka = KeyAgreement.getInstance("X25519")
        ka.init(priv)
        ka.doPhase(pub, true)
        return ka.generateSecret()
    }

    private fun kdfRoot(rk: ByteArray, dhOut: ByteArray): Pair<ByteArray, ByteArray> {
        val prk = hmac(rk, dhOut)
        val newRk = hmac(prk, "DR-ROOT\u0001".toByteArray(StandardCharsets.US_ASCII))
        val newCk = hmac(prk, "DR-CHAIN\u0002".toByteArray(StandardCharsets.US_ASCII))
        return newRk to newCk
    }

    private fun kdfChain(ck: ByteArray): Pair<ByteArray, ByteArray> =
        hmac(ck, byteArrayOf(0x02)) to hmac(ck, byteArrayOf(0x01))

    private fun aeadSeal(key32: ByteArray, aad: ByteArray, pt: ByteArray): ByteArray {
        val nonce = hmac(key32, "NONCE".toByteArray(StandardCharsets.US_ASCII)).copyOf(12)
        val c = Cipher.getInstance("ChaCha20-Poly1305")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key32, "ChaCha20"), IvParameterSpec(nonce))
        c.updateAAD(aad)
        return c.doFinal(pt)
    }

    private fun aeadOpen(key32: ByteArray, aad: ByteArray, ct: ByteArray): ByteArray {
        val nonce = hmac(key32, "NONCE".toByteArray(StandardCharsets.US_ASCII)).copyOf(12)
        val c = Cipher.getInstance("ChaCha20-Poly1305")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key32, "ChaCha20"), IvParameterSpec(nonce))
        c.updateAAD(aad)
        return c.doFinal(ct)
    }

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray {
        val m = Mac.getInstance("HmacSHA256")
        m.init(SecretKeySpec(key, "HmacSHA256"))
        return m.doFinal(data)
    }
}
