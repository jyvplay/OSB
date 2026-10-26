package dev.fusion.privacy

/**
 * Timestamp coarsening for privacy-preserving logs: floor an epoch
 * second to minute/hour/day buckets so stored events cannot be
 * correlated at full precision.
 *
 * HONESTY (same posture as IpRedact): coarsening REDUCES precision;
 * it is NOT anonymity — an hourly bucket still orders events and
 * joins with other hourly data. The scope string says so and callers
 * surface that wording.
 *
 * Declared narrowings: UTC epoch arithmetic only (no zones — a "local
 * midnight" bucket is a calendar feature with DST edges, refused
 * here); negative epochs refuse (this app logs no pre-1970 events;
 * accepting them would only mask bugs).
 */
object TimeCoarse {
    const val SCOPE = "UTC floor to minute/hour/day; reduces precision, NOT anonymity; no zones, no pre-1970."

    enum class Grain(val seconds: Long) { MINUTE(60), HOUR(3600), DAY(86400) }

    fun floor(epochS: Long, grain: Grain): Long {
        require(epochS >= 0) { "pre-1970 epoch $epochS refused" }
        return epochS / grain.seconds * grain.seconds
    }

    fun bucketIndex(epochS: Long, grain: Grain): Long = floor(epochS, grain) / grain.seconds

    fun sameBucket(a: Long, b: Long, grain: Grain): Boolean = bucketIndex(a, grain) == bucketIndex(b, grain)

    /** Worst-case information retained: bucket count per day. */
    fun bucketsPerDay(grain: Grain): Long = 86400 / grain.seconds
}
