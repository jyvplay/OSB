import dev.fusion.documents.EditorSession

// Compiler regression only; actual production types, not Android substitutes.
fun pendingName(session: EditorSession): String {
    val pendingCopy = session.pendingCopy
    return if (pendingCopy != null) pendingCopy.name else ""
}
fun nestedRequestLength(session: EditorSession): Int {
    val requestId = session.pendingCopy?.requestId
    return if (requestId != null) requestId.length else 0
}
