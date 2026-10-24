package dev.fusion.media

/**
 * Decibel/linear conversions for the media surface, with the
 * amplitude/power distinction EXPLICIT in the API (dB = 20·log10 for
 * amplitude ratios, 10·log10 for power ratios — conflating them is
 * the classic audio bug, so there is no unqualified `toDb` here).
 *
 * Executed anchors: ±20 dB amplitude is exactly ×10/÷10; -6.02 dB is
 * half amplitude; conversions invert to 1e-12.
 *
 * Declared narrowings: ratios only — no SPL reference, no loudness
 * (LUFS is gated weighting, a different kernel if ever needed);
 * non-positive ratios refuse (log of zero is not a level).
 */
object GainDb {
    const val SCOPE = "Amplitude(20log)/power(10log) ratio conversions only; no SPL reference, no loudness."

    fun amplitudeToDb(ratio: Double): Double {
        require(ratio > 0.0 && ratio.isFinite()) { "amplitude ratio $ratio" }
        return 20.0 * Math.log10(ratio)
    }

    fun dbToAmplitude(db: Double): Double {
        require(db.isFinite()) { "dB $db" }
        return Math.pow(10.0, db / 20.0)
    }

    fun powerToDb(ratio: Double): Double {
        require(ratio > 0.0 && ratio.isFinite()) { "power ratio $ratio" }
        return 10.0 * Math.log10(ratio)
    }

    fun dbToPower(db: Double): Double {
        require(db.isFinite()) { "dB $db" }
        return Math.pow(10.0, db / 10.0)
    }

    /** Combine two gains in dB (multiplication in linear domain). */
    fun combine(dbA: Double, dbB: Double): Double {
        require(dbA.isFinite() && dbB.isFinite()) { "non-finite gain" }
        return dbA + dbB
    }
}
