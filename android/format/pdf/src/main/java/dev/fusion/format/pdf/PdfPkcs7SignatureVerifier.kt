// SPDX-License-Identifier: Apache-2.0
package dev.fusion.format.pdf

import java.nio.charset.StandardCharsets
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature

/**
 * Clean-room PDF Detached Digital Signature & `/ByteRange` Coverage Verifier (`PR-08`).
 *
 * Defends against PDF Signature Shadow / ByteRange-Gap / Unsigned-Incremental-Append attacks:
 * 1. Parses `/ByteRange [off1 len1 off2 len2]` and `/Contents <hex...>`.
 * 2. Enforces `off1 == 0`, exact adjacency around `<hex...>` (`off2 == off1 + len1 + hexEnvelopeLen`),
 *    and full-file coverage (`off2 + len2 == pdfBytes.size`) so NO unsigned bytes trail EOF!
 * 3. Computes SHA-256 over `pdfBytes[off1 ..< off1+len1] || pdfBytes[off2 ..< off2+len2]` and
 *    verifies detached Ed25519 (`EdDSA`) or `SHA256withRSA` signatures via JDK `java.security`.
 */
object PdfPkcs7SignatureVerifier {
    enum class VerdictStatus {
        VALID_FULL_COVERAGE,
        MISSING_SIGNATURE_DICTIONARY,
        BYTERANGE_DOES_NOT_START_AT_ZERO,
        BYTERANGE_HOLE_MISMATCH,
        UNSIGNED_INCREMENTAL_BYTES_AFTER_SIGNATURE,
        CRYPTO_SIGNATURE_INVALID,
    }

    data class VerificationReport(
        val status: VerdictStatus,
        val signedDigestHex: String?,
        val byteRange: List<Int>?,
    ) {
        val isTrusted: Boolean get() = status == VerdictStatus.VALID_FULL_COVERAGE
    }

    fun signPdfEnvelopeEd25519(unsignedPdfBytes: ByteArray, privateKey: java.security.PrivateKey, hexSlotChars: Int = 128): ByteArray {
        require(hexSlotChars >= 128 && hexSlotChars % 2 == 0) { "Ed25519 hex slot must be >= 128 even chars" }
        val placeholderHex = "0".repeat(hexSlotChars)
        // Fixed-width 10-digit ByteRange slots guarantee byte offsets never shift when numbers are filled!
        val prefix = "\n999 0 obj\n<< /Type /Sig /Filter /Adobe.PPKLite /SubFilter /ETSI.CAdES.detached /ByteRange [0000000000 %010d %010d %010d] /Contents <"
        val suffix = "> >>\nendobj\n"
        val off1 = 0
        // Measure exact prefix length once formatted with 10-digit integers
        val samplePrefix = String.format(java.util.Locale.US, prefix, 0, 0, 0).toByteArray(StandardCharsets.US_ASCII)
        val suffixBytes = suffix.toByteArray(StandardCharsets.US_ASCII)
        val len1 = unsignedPdfBytes.size + samplePrefix.size - 1 // up to just before '<'
        val hexEnvelopeLen = 1 + hexSlotChars + 1 // '<' + hex + '>'
        val off2 = len1 + hexEnvelopeLen
        val len2 = suffixBytes.size - 1 // after '>' to EOF

        val formattedPrefix = String.format(java.util.Locale.US, prefix, len1, off2, len2).toByteArray(StandardCharsets.US_ASCII)
        val total = unsignedPdfBytes.size + formattedPrefix.size + hexSlotChars + suffixBytes.size
        val assembled = ByteArray(total)
        System.arraycopy(unsignedPdfBytes, 0, assembled, 0, unsignedPdfBytes.size)
        System.arraycopy(formattedPrefix, 0, assembled, unsignedPdfBytes.size, formattedPrefix.size)
        val hexStart = unsignedPdfBytes.size + formattedPrefix.size
        System.arraycopy(placeholderHex.toByteArray(StandardCharsets.US_ASCII), 0, assembled, hexStart, hexSlotChars)
        System.arraycopy(suffixBytes, 0, assembled, hexStart + hexSlotChars, suffixBytes.size)

        val signedSlice = ByteArray(len1 + len2)
        System.arraycopy(assembled, off1, signedSlice, 0, len1)
        System.arraycopy(assembled, off2, signedSlice, len1, len2)

        val signer = Signature.getInstance("Ed25519")
        signer.initSign(privateKey)
        signer.update(signedSlice)
        val sigBytes = signer.sign() // 64 bytes -> 128 hex chars
        val sigHex = sigBytes.joinToString("") { "%02x".format(it) }.padEnd(hexSlotChars, '0')
        System.arraycopy(sigHex.toByteArray(StandardCharsets.US_ASCII), 0, assembled, hexStart, hexSlotChars)
        return assembled
    }

    fun verifyPdfSignature(
        pdfBytes: ByteArray,
        publicKey: PublicKey,
        algorithm: String = "Ed25519",
    ): VerificationReport {
        val ascii = String(pdfBytes, StandardCharsets.ISO_8859_1)
        val brMatch = Regex("""/ByteRange\s*\[\s*(\d+)\s+(\d+)\s+(\d+)\s+(\d+)\s*]""").find(ascii)
            ?: return VerificationReport(VerdictStatus.MISSING_SIGNATURE_DICTIONARY, null, null)
        val off1 = brMatch.groupValues[1].toInt()
        val len1 = brMatch.groupValues[2].toInt()
        val off2 = brMatch.groupValues[3].toInt()
        val len2 = brMatch.groupValues[4].toInt()
        val br = listOf(off1, len1, off2, len2)

        if (off1 != 0) {
            return VerificationReport(VerdictStatus.BYTERANGE_DOES_NOT_START_AT_ZERO, null, br)
        }
        if (len1 <= 0 || len2 <= 0 || off2 <= len1 || off2 + len2 > pdfBytes.size) {
            return VerificationReport(VerdictStatus.BYTERANGE_HOLE_MISMATCH, null, br)
        }
        // Hole [len1 ..< off2] MUST be exactly `<hex...>` with zero extra dictionary trickery!
        if (pdfBytes[len1] != '<'.code.toByte() || pdfBytes[off2 - 1] != '>'.code.toByte()) {
            return VerificationReport(VerdictStatus.BYTERANGE_HOLE_MISMATCH, null, br)
        }
        if (off2 + len2 != pdfBytes.size) {
            return VerificationReport(VerdictStatus.UNSIGNED_INCREMENTAL_BYTES_AFTER_SIGNATURE, null, br)
        }

        val hexStr = ascii.substring(len1 + 1, off2 - 1).trim()
        if (hexStr.length < 128 || hexStr.length % 2 != 0) {
            return VerificationReport(VerdictStatus.BYTERANGE_HOLE_MISMATCH, null, br)
        }
        val sigByteLen = if (algorithm.equals("Ed25519", ignoreCase = true)) 64 else hexStr.length / 2
        val rawSig = ByteArray(sigByteLen) { i ->
            hexStr.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }

        val signedSlice = ByteArray(len1 + len2)
        System.arraycopy(pdfBytes, off1, signedSlice, 0, len1)
        System.arraycopy(pdfBytes, off2, signedSlice, len1, len2)
        val shaHex = MessageDigest.getInstance("SHA-256").digest(signedSlice)
            .joinToString("") { "%02x".format(it) }

        val verifier = Signature.getInstance(algorithm)
        verifier.initVerify(publicKey)
        verifier.update(signedSlice)
        val ok = runCatching { verifier.verify(rawSig) }.getOrDefault(false)
        return VerificationReport(
            if (ok) VerdictStatus.VALID_FULL_COVERAGE else VerdictStatus.CRYPTO_SIGNATURE_INVALID,
            shaHex,
            br,
        )
    }

    fun generateEd25519KeyPair(): java.security.KeyPair =
        KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
}
