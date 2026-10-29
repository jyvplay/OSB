// SPDX-License-Identifier: Apache-2.0
package dev.fusion.privacy

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Base64

/**
 * Clean-room IETF `RFC 9849` (*TLS Encrypted Client Hello*) binary `ECHConfigList` (`0xFE0D`)
 * codec, DNS `HTTPS`/`SVCB` (`RFC 9460` `SvcParamKey ech = 5`) parser, `ECH GREASE` decoy
 * generator, and outer/inner `ClientHello` SNI leak & downgrade validator.
 *
 * Wire layout per `RFC 9849` §4:
 * ```
 * opaque ECHConfigList<1..2^16-1>; // 2-byte big-endian total byte length prefix
 * struct {
 *   uint16 version = 0xfe0d;
 *   uint16 length;
 *   select (ECHConfig.version) {
 *     case 0xfe0d: ECHConfigContents contents;
 *   }
 * } ECHConfig;
 * struct {
 *   HpkeKeyConfig key_config; // config_id(1B), kem_id(2B), public_key<1..2^16-1>, cipher_suites<4..2^16-4>
 *   uint8 maximum_name_length;
 *   opaque public_name<1..255>; // valid DNS hostname, no trailing dot, no IP literal
 *   Extension extensions<0..2^16-1>;
 * } ECHConfigContents;
 * ```
 */
object TlsEchConfigCodec {
    const val ECH_VERSION_RFC9849 = 0xFE0D
    const val KEM_DHKEM_X25519_HKDF_SHA256 = 0x0020
    const val KDF_HKDF_SHA256 = 0x0001
    const val AEAD_AES_128_GCM = 0x0001
    const val AEAD_CHACHA20_POLY1305 = 0x0003
    const val DNS_HTTPS_SVCPARAM_ECH = 5

    data class HpkeSymmetricSuite(val kdfId: Int, val aeadId: Int) {
        init {
            require(kdfId in 1..0xFFFF && aeadId in 1..0xFFFF) { "invalid HPKE KDF/AEAD IDs" }
        }
    }

    data class EchConfig(
        val configId: Int,
        val kemId: Int,
        val hpkePublicKey: ByteArray,
        val cipherSuites: List<HpkeSymmetricSuite>,
        val maximumNameLength: Int,
        val publicName: String,
    ) {
        init {
            require(configId in 0..255) { "configId must be uint8" }
            require(kemId in 1..0xFFFF) { "invalid kemId" }
            require(hpkePublicKey.size in 32..133) { "HPKE public key length ${hpkePublicKey.size} outside 32..133" }
            require(cipherSuites.isNotEmpty()) { "at least one HPKE cipher suite required" }
            require(maximumNameLength in 0..255) { "maximumNameLength must be uint8" }
            require(isValidPublicName(publicName)) { "invalid RFC 9849 ECH public_name '$publicName'" }
        }
    }

    data class HandshakePrivacyAudit(
        val compliant: Boolean,
        val violationReason: String?,
    )

    private val csprng = SecureRandom()

    fun isValidPublicName(name: String): Boolean {
        if (name.isEmpty() || name.length > 255 || name.endsWith(".") || name.startsWith(".")) return false
        val labels = name.split('.')
        if (labels.any { it.isEmpty() || it.length > 63 || it.startsWith("-") || it.endsWith("-") }) return false
        if (labels.any { lbl -> !lbl.all { it.isLetterOrDigit() || it == '-' } }) return false
        // RFC 9849 §4: public_name MUST NOT be an IPv4 literal
        if (labels.size == 4 && labels.all { it.all(Char::isDigit) }) return false
        return true
    }

    fun encodeConfigList(configs: List<EchConfig>): ByteArray {
        require(configs.isNotEmpty()) { "ECHConfigList requires >= 1 ECHConfig" }
        val innerList = ByteArrayOutputStream()
        for (c in configs) {
            val contents = ByteArrayOutputStream()
            contents.write(c.configId and 0xFF)
            writeU16(contents, c.kemId)
            writeU16(contents, c.hpkePublicKey.size)
            contents.write(c.hpkePublicKey)
            writeU16(contents, c.cipherSuites.size * 4)
            for (s in c.cipherSuites) {
                writeU16(contents, s.kdfId)
                writeU16(contents, s.aeadId)
            }
            contents.write(c.maximumNameLength and 0xFF)
            val pubBytes = c.publicName.toByteArray(StandardCharsets.US_ASCII)
            contents.write(pubBytes.size and 0xFF)
            contents.write(pubBytes)
            writeU16(contents, 0) // extensions<0..2^16-1> empty

            val contentBytes = contents.toByteArray()
            writeU16(innerList, ECH_VERSION_RFC9849)
            writeU16(innerList, contentBytes.size)
            innerList.write(contentBytes)
        }
        val listBytes = innerList.toByteArray()
        require(listBytes.size in 1..65_535) { "ECHConfigList exceeds uint16 length" }
        val framed = ByteArrayOutputStream(2 + listBytes.size)
        writeU16(framed, listBytes.size)
        framed.write(listBytes)
        return framed.toByteArray()
    }

    fun decodeConfigList(bytes: ByteArray): List<EchConfig> {
        require(bytes.size >= 4) { "ECHConfigList shorter than 4 bytes" }
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        val totalLen = buf.short.toInt() and 0xFFFF
        require(totalLen > 0 && 2 + totalLen == bytes.size) {
            "ECHConfigList length prefix ($totalLen) != remaining bytes (${bytes.size - 2})"
        }
        val out = ArrayList<EchConfig>()
        while (buf.hasRemaining()) {
            require(buf.remaining() >= 4) { "truncated ECHConfig header" }
            val ver = buf.short.toInt() and 0xFFFF
            val cfgLen = buf.short.toInt() and 0xFFFF
            require(buf.remaining() >= cfgLen) { "truncated ECHConfig body" }
            val cfgEnd = buf.position() + cfgLen
            if (ver != ECH_VERSION_RFC9849) {
                // RFC 9849 §4: clients MUST ignore ECHConfig structures with unfamiliar versions
                buf.position(cfgEnd)
                continue
            }
            val configId = buf.get().toInt() and 0xFF
            val kemId = buf.short.toInt() and 0xFFFF
            val pkLen = buf.short.toInt() and 0xFFFF
            require(pkLen > 0 && buf.position() + pkLen <= cfgEnd) { "invalid HPKE pubkey length" }
            val pk = ByteArray(pkLen)
            buf.get(pk)
            val suitesBytes = buf.short.toInt() and 0xFFFF
            require(suitesBytes >= 4 && suitesBytes % 4 == 0 && buf.position() + suitesBytes <= cfgEnd) {
                "invalid HPKE cipher_suites length $suitesBytes"
            }
            val suites = List(suitesBytes / 4) {
                HpkeSymmetricSuite(buf.short.toInt() and 0xFFFF, buf.short.toInt() and 0xFFFF)
            }
            val maxNameLen = buf.get().toInt() and 0xFF
            val pubNameLen = buf.get().toInt() and 0xFF
            require(pubNameLen > 0 && buf.position() + pubNameLen <= cfgEnd) { "invalid public_name length" }
            val pubBytes = ByteArray(pubNameLen)
            buf.get(pubBytes)
            val pubName = String(pubBytes, StandardCharsets.US_ASCII)
            val extLen = buf.short.toInt() and 0xFFFF
            require(buf.position() + extLen == cfgEnd) { "ECHConfig extension length mismatch" }
            buf.position(cfgEnd)
            out.add(EchConfig(configId, kemId, pk, suites, maxNameLen, pubName))
        }
        return out
    }

    fun extractFromHttpsRecordPresentation(httpsPresentation: String): List<EchConfig> {
        val m = Regex("""\bech="?([A-Za-z0-9+/=]+)"?""").find(httpsPresentation)
            ?: return emptyList()
        val raw = Base64.getDecoder().decode(m.groupValues[1])
        return decodeConfigList(raw)
    }

    fun generateGreaseOuterExtension(payloadLen: Int = 144): ByteArray {
        require(payloadLen in 64..512) { "GREASE payloadLen outside 64..512" }
        val out = ByteArrayOutputStream()
        out.write(0x00) // ClientHelloOuter ECHClientHelloType = outer(0)
        writeU16(out, KDF_HKDF_SHA256)
        writeU16(out, AEAD_AES_128_GCM)
        val randId = ByteArray(1).also(csprng::nextBytes)
        out.write(randId[0].toInt() and 0xFF)
        val enc = ByteArray(32).also(csprng::nextBytes)
        writeU16(out, enc.size)
        out.write(enc)
        val payload = ByteArray(payloadLen).also(csprng::nextBytes)
        writeU16(out, payload.size)
        out.write(payload)
        return out.toByteArray()
    }

    fun auditOuterClientHello(
        config: EchConfig,
        innerTargetSni: String,
        outerVisibleSni: String,
        serverNegotiatedEch: Boolean,
        retryConfigsProvided: Boolean,
    ): HandshakePrivacyAudit {
        if (outerVisibleSni.equals(innerTargetSni, ignoreCase = true) &&
            !config.publicName.equals(innerTargetSni, ignoreCase = true)
        ) {
            return HandshakePrivacyAudit(false, "CRITICAL_SNI_LEAK: ClientHelloOuter exposed real target SNI '$innerTargetSni'")
        }
        if (!outerVisibleSni.equals(config.publicName, ignoreCase = true)) {
            return HandshakePrivacyAudit(false, "OUTER_SNI_MISMATCH: expected '${config.publicName}', got '$outerVisibleSni'")
        }
        if (!serverNegotiatedEch && !retryConfigsProvided) {
            return HandshakePrivacyAudit(false, "ECH_SILENT_DOWNGRADE_DETECTED: server offered ECH in DNS but neither accepted nor sent retry_configs")
        }
        return HandshakePrivacyAudit(true, null)
    }

    private fun writeU16(out: ByteArrayOutputStream, v: Int) {
        out.write((v ushr 8) and 0xFF)
        out.write(v and 0xFF)
    }
}
