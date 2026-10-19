package dev.fusion.lok

/** Native Android Office loading remains gated. The module also contains the bounded browser/WASM
 * OfficeSession.js adapter; that has its own real-engine experiment and is not a bundled Android runtime.
 */
object LokBridge {
    fun availability(): String =
        "Native Android Office is not enabled. Experimental bounded Writer/WASM adapter source is provided separately; engine payload, Android hosting, memory, fidelity, security and license gates remain open."
}
