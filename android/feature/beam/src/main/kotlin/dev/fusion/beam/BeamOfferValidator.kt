// SPDX-License-Identifier: Apache-2.0
package dev.fusion.beam

/**
 * Bounded beam offer validator. Validates incoming beam share offers:
 * checks sender ID, payload size, expiry time, and allowed MIME types.
 * Rejects offers that exceed size limits, are already expired, or use
 * disallowed MIME types. Does not perform the actual transfer — the beam
 * transport layer handles that.
 *
 * Declared narrowings: at most [MAX_OFFERS] tracked offers; sender IDs at
 * most [MAX_ID_LEN] chars; payload sizes at most [MAX_PAYLOAD_BYTES];
 * MIME types at most [MAX_MIME_LEN] chars matching [MIME_RE]; expiry
 * timestamps in milliseconds must be non-negative and at most [MAX_TS].
 */
class BeamOfferValidator private constructor(
    private val offers: LinkedHashMap<String, BeamOffer>
) {
    companion object {
        const val MAX_OFFERS = 512
        const val MAX_ID_LEN = 128
        const val MAX_PAYLOAD_BYTES = 1L shl 30 // 1 GiB
        const val MAX_MIME_LEN = 128
        const val MAX_TS = Long.MAX_VALUE
        private val ID_RE = Regex("[a-zA-Z0-9_-]{1,64}")
        private val MIME_RE = Regex("[a-z]+/[a-z0-9.+-]+")

        data class BeamOffer(
            val offerId: String,
            val senderId: String,
            val payloadSizeBytes: Long,
            val mimeType: String,
            val expiresAtMs: Long
        )

        data class ValidationResult(
            val valid: Boolean,
            val reason: String?
        )

        fun empty(): BeamOfferValidator = BeamOfferValidator(LinkedHashMap())
    }

    fun validate(offerId: String, senderId: String, payloadSizeBytes: Long,
                 mimeType: String, expiresAtMs: Long): ValidationResult {
        require(ID_RE.matches(offerId)) { "invalid offer id: $offerId" }
        require(offerId.length <= MAX_ID_LEN) { "offer id too long" }
        require(ID_RE.matches(senderId)) { "invalid sender id: $senderId" }
        require(senderId.length <= MAX_ID_LEN) { "sender id too long" }
        require(payloadSizeBytes >= 0 && payloadSizeBytes <= MAX_PAYLOAD_BYTES) {
            "payload size out of bounds"
        }
        require(MIME_RE.matches(mimeType)) { "invalid MIME type: $mimeType" }
        require(mimeType.length <= MAX_MIME_LEN) { "MIME type too long" }
        require(expiresAtMs >= 0 && expiresAtMs <= MAX_TS) { "expiry out of bounds" }

        // Check expiry
        val now = System.currentTimeMillis()
        if (expiresAtMs <= now) {
            return ValidationResult(false, "offer expired")
        }

        // Check for duplicate offer
        if (offers.containsKey(offerId)) {
            return ValidationResult(false, "duplicate offer id")
        }

        return ValidationResult(true, null)
    }

    fun recordOffer(offer: BeamOffer) {
        require(offers.size < MAX_OFFERS || offers.containsKey(offer.offerId)) {
            "too many offers: $MAX_OFFERS"
        }
        offers[offer.offerId] = offer
    }

    fun offerCount(): Int = offers.size
    fun removeOffer(offerId: String): Boolean = offers.remove(offerId) != null
    fun clear() = offers.clear()
}
