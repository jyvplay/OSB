import dev.fusion.documents.EditorSession
import dev.fusion.documents.PendingCopy

// EXPECTED COMPILER ERRORS against the separately compiled production library.
// The first is the pre-repair EditorScreen pattern; the second attacks a nested
// nullable public property even after its containing object has been stabilized.
fun pendingName(session: EditorSession): String =
    if (session.pendingCopy != null) session.pendingCopy.name else ""
fun nestedRequestLength(pending: PendingCopy): Int =
    if (pending.requestId != null) pending.requestId.length else 0
