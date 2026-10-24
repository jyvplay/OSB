package dev.fusion.beam

/**
 * BitTorrent piece arithmetic (BEP 3: content is split into
 * piece-length blocks; the final piece may be short; `pieces` holds
 * one 20-byte SHA-1 per piece).
 *
 * Declared narrowings: piece length must be a power of two and at
 * least 16 KiB (BEP 3 says "almost always a power of two"; this
 * kernel promotes the convention to a refusal so pathological values
 * surface loudly — a torrent that needs an exception needs a
 * decision); sizes are positive (empty payloads have no pieces to
 * plan); no hashing here (MagnetParse/Bencode own their layers, SHA-1
 * computation is IO-bound work elsewhere).
 */
object PiecePlan {
    const val SCOPE = "Piece count/size/offset algebra; power-of-two >=16KiB promoted from convention to refusal."
    const val MIN_PIECE = 16384
    const val SHA1_BYTES = 20

    fun validatePieceLength(pieceLength: Long): Long {
        require(pieceLength >= MIN_PIECE) { "piece length $pieceLength below 16 KiB" }
        require(pieceLength and (pieceLength - 1) == 0L) { "piece length $pieceLength is not a power of two" }
        return pieceLength
    }

    data class Plan(val totalSize: Long, val pieceLength: Long, val pieceCount: Long, val lastPieceSize: Long) {
        val piecesFieldBytes: Long get() = pieceCount * SHA1_BYTES
    }

    fun plan(totalSize: Long, pieceLength: Long): Plan {
        require(totalSize > 0) { "total size must be positive" }
        validatePieceLength(pieceLength)
        val count = (totalSize + pieceLength - 1) / pieceLength
        val last = totalSize - (count - 1) * pieceLength
        return Plan(totalSize, pieceLength, count, last)
    }

    fun pieceSize(p: Plan, index: Long): Long {
        require(index in 0 until p.pieceCount) { "piece $index outside 0..${p.pieceCount - 1}" }
        return if (index == p.pieceCount - 1) p.lastPieceSize else p.pieceLength
    }

    fun offsetOf(p: Plan, index: Long): Long {
        require(index in 0 until p.pieceCount) { "piece $index out of range" }
        return index * p.pieceLength
    }

    fun pieceOf(p: Plan, byteOffset: Long): Long {
        require(byteOffset in 0 until p.totalSize) { "offset $byteOffset outside the payload" }
        return byteOffset / p.pieceLength
    }
}
