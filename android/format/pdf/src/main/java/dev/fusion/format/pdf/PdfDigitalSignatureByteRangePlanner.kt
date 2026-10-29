// SPDX-License-Identifier: Apache-2.0
package dev.fusion.format.pdf

import java.security.MessageDigest

/**
 * ISO 32000-2 §12.8 (`ETSI.CAdES.detached` / `adbe.pkcs7.detached`) PDF incremental digital
 * signature `/ByteRange [0 off1 end1 len2]` placeholder planner and SHA-256 digest calculator
 * verifying zero unsigned gap bytes outside `/Contents <...>`.
 */
object PdfDigitalSignatureByteRangePlanner {
    const val SCOPE =
        "ISO 32000-2 §12.8 PDF incremental /Sig dictionary /ByteRange placeholder planner and full-file coverage SHA-256 verifier."

    data class PreparedPdfSignature(
        val pdfWithPlaceholder: ByteArray,
        val byteRange: IntArray, // [0, contentsStart, contentsEnd, trailingLen]
        val signedBytesSha256Hex: String,
    )

    fun appendSignaturePlaceholder(
        originalPdf: ByteArray,
        signerCommonName: String,
        signatureHexCapacityBytes: Int = 512,
    ): PreparedPdfSignature {
        require(originalPdf.size >= 8 && String(originalPdf, 0, 5, Charsets.US_ASCII) == "%PDF-") {
            "Input must start with %PDF-"
        }
        require(signatureHexCapacityBytes in 64..16384)
        val hexChars = signatureHexCapacityBytes * 2
        // Fixed-width 10-digit ByteRange slots so patching offsets never shifts /Contents offset!
        val brTemplate = "/ByteRange [0000000000 0000000000 0000000000 0000000000]"
        val prefix = ("\n999 0 obj\n<< /Type /Sig /Filter /Adobe.PPKLite /SubFilter /ETSI.CAdES.detached " +
            "/Name (${signerCommonName.replace("(", "").replace(")", "")}) $brTemplate /Contents <").toByteArray(Charsets.US_ASCII)
        val placeholderHex = ByteArray(hexChars) { '0'.code.toByte() }
        val suffix = "> >>\nendobj\n%%EOF\n".toByteArray(Charsets.US_ASCII)

        val totalSize = originalPdf.size + prefix.size + placeholderHex.size + suffix.size
        val contentsOpenAngleOffset = originalPdf.size + prefix.size - 1 // includes '<'
        val contentsCloseAngleEnd = contentsOpenAngleOffset + 1 + placeholderHex.size + 1 // right after '>'
        val trailingLen = totalSize - contentsCloseAngleEnd

        val patchedPrefixStr = String(prefix, Charsets.US_ASCII).replace(
            brTemplate,
            "/ByteRange [%010d %010d %010d %010d]".format(0, contentsOpenAngleOffset, contentsCloseAngleEnd, trailingLen),
        )
        val patchedPrefix = patchedPrefixStr.toByteArray(Charsets.US_ASCII)
        check(patchedPrefix.size == prefix.size)

        val assembled = ByteArray(totalSize)
        System.arraycopy(originalPdf, 0, assembled, 0, originalPdf.size)
        System.arraycopy(patchedPrefix, 0, assembled, originalPdf.size, patchedPrefix.size)
        System.arraycopy(placeholderHex, 0, assembled, originalPdf.size + patchedPrefix.size, placeholderHex.size)
        System.arraycopy(suffix, 0, assembled, originalPdf.size + patchedPrefix.size + placeholderHex.size, suffix.size)

        val md = MessageDigest.getInstance("SHA-256")
        md.update(assembled, 0, contentsOpenAngleOffset)
        md.update(assembled, contentsCloseAngleEnd, trailingLen)
        val digestHex = md.digest().joinToString("") { "%02x".format(it) }

        return PreparedPdfSignature(
            pdfWithPlaceholder = assembled,
            byteRange = intArrayOf(0, contentsOpenAngleOffset, contentsCloseAngleEnd, trailingLen),
            signedBytesSha256Hex = digestHex,
        )
    }

    fun verifyFullCoverageByteRange(pdfBytes: ByteArray, byteRange: IntArray): Boolean {
        if (byteRange.size != 4) return false
        val (o0, l0, o1, l1) = byteRange
        if (o0 != 0 || l0 <= 0 || o1 <= l0 || l1 <= 0) return false
        if (o1 + l1 != pdfBytes.size) return false
        // Gap [l0, o1) must be strictly '<' ...hex... '>' (prevents shadow-attack unsigned injections)
        if (pdfBytes[l0] != '<'.code.toByte() || pdfBytes[o1 - 1] != '>'.code.toByte()) return false
        return true
    }
}
