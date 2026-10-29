package dev.fusion.shell

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Clean-room Zero-Trust Inbound Deep-Link (`fusion://workspace/...`) Sanitizer (Apache-2.0)
 * blocking `intent://` scheme redirection, URL-encoded `..` path traversal, and unexported route
 * spoofing (`OWASP MASVS-PLATFORM-2`).
 */
object WorkspaceDeepLinkIntentSanitizer {
    const val SCOPE =
        "Zero-trust fusion:// deep-link URI route allowlist & double-URL-decoded traversal/intent-scheme blocker"

    private val ALLOWED_ROUTES = setOf("editor", "calc", "browser", "vault", "notes", "pdf", "privacy")

    sealed interface DeepLinkVerdict {
        data class Accepted(val route: String, val safeDocId: String?) : DeepLinkVerdict
        data class Rejected(val reason: String) : DeepLinkVerdict
    }

    fun inspect(rawUri: String): DeepLinkVerdict {
        val trimmed = rawUri.trim()
        if (trimmed.isEmpty() || trimmed.length > 512) return DeepLinkVerdict.Rejected("EMPTY_OR_OVERSIZED_URI")
        // Decode twice to defeat `%252e%252e` double-encoding bypasses!
        val pass1 = runCatching { URLDecoder.decode(trimmed, StandardCharsets.UTF_8) }.getOrElse { return DeepLinkVerdict.Rejected("MALFORMED_PERCENT_ENCODING") }
        val pass2 = runCatching { URLDecoder.decode(pass1, StandardCharsets.UTF_8) }.getOrElse { pass1 }
        if (".." in pass2 || '\u0000' in pass2 || '\\' in pass2 || "intent:" in pass2.lowercase() || "javascript:" in pass2.lowercase()) {
            return DeepLinkVerdict.Rejected("TRAVERSAL_OR_INJECTED_SCHEME")
        }
        val uri = runCatching { URI(trimmed) }.getOrElse { return DeepLinkVerdict.Rejected("INVALID_URI_SYNTAX") }
        if (!uri.scheme.equals("fusion", ignoreCase = true) || !uri.host.equals("workspace", ignoreCase = true)) {
            return DeepLinkVerdict.Rejected("UNTRUSTED_SCHEME_OR_AUTHORITY")
        }
        val segs = uri.path.orEmpty().split('/').filter { it.isNotEmpty() }
        val route = segs.getOrNull(0)?.lowercase() ?: return DeepLinkVerdict.Rejected("MISSING_WORKSPACE_ROUTE")
        if (route !in ALLOWED_ROUTES) return DeepLinkVerdict.Rejected("DISALLOWED_ROUTE_$route")
        val docId = segs.getOrNull(1)
        if (docId != null && !Regex("^[A-Za-z0-9_-]{1,64}$").matches(docId)) {
            return DeepLinkVerdict.Rejected("INVALID_DOC_ID_TOKEN")
        }
        return DeepLinkVerdict.Accepted(route, docId)
    }
}
