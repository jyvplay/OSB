package dev.fusion.lok

/**
 * Placeholder bridge boundary for a future isolated LibreOfficeKit module.
 * Nothing here can open or edit a document yet; this exists only so the
 * module graph is explicit and consumers can test for presence honestly.
 */
object LokBridge {
    fun availability(): String =
        "Not implemented. LibreOfficeKit is excluded from this build until payload size, performance, and licensing are reviewed."
}
