package dev.fusion.together

/** Conditional causal bounds, in nanoseconds, not a synchronization result.
 * Assumes equal clock rates during the exchange and trustworthy timestamps. Clock read
 * quantization/drift, asymmetric delays and a dishonest authenticated peer are NOT eliminated.
 */
data class PeerClock(val offsetLowerNanos: Long, val offsetUpperNanos: Long, val roundTripNanos: Long) {
    init {
        require(roundTripNanos >= 0 && offsetLowerNanos <= offsetUpperNanos)
        require(java.math.BigInteger.valueOf(offsetUpperNanos).subtract(java.math.BigInteger.valueOf(offsetLowerNanos)) == java.math.BigInteger.valueOf(roundTripNanos))
    }
    val midpointNanos: Long get() = Math.addExact(offsetLowerNanos, roundTripNanos / 2)
    companion object {
        fun from(localSend: Long, peerReceive: Long, peerSend: Long, localReceive: Long): PeerClock {
            require(listOf(localSend, peerReceive, peerSend, localReceive).all { it >= 0 })
            require(localReceive >= localSend && peerSend >= peerReceive)
            val local = localReceive - localSend
            val remote = peerSend - peerReceive
            require(remote <= local) { "Clock sample violates equal-rate causal model" }
            return PeerClock(peerSend - localReceive, peerReceive - localSend, local - remote)
        }
    }
}
