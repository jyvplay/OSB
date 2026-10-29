// SPDX-License-Identifier: Apache-2.0
package dev.fusion.index

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Clean-room Local-First Search Federation Engine (`SR-04`, `SR-07`):
 * - Always ranks local files/notes/commands strictly above any opt-in web or external-app hits.
 * - Produces zero network URLs unless `optInWebResults == true` (`SR-04`).
 * - Admits `AppSearch` / `AppFunctions` external app slices only for explicitly allowlisted
 *   packages when `optInExternalAppSearch == true` (`SR-07`).
 */
object OptInWebAppSearchFederation {
    const val SCOPE =
        "Pure-JVM local-first search ranker keeping opt-in web links strictly below local results (SR-04) and gating AppSearch/AppFunctions external content by explicit package opt-in (SR-07)."

    enum class ResultTier { LOCAL_PRIMARY, EXTERNAL_APP_FUNCTION, OPT_IN_WEB_FOOTER }

    data class FederatedHit(
        val id: String,
        val title: String,
        val tier: ResultTier,
        val score: Int,
        val targetUriOrAction: String,
    )

    fun federate(
        rawQuery: String,
        localHits: List<Pair<String, Int>>, // title -> score
        externalAppCandidates: List<Triple<String, String, String>>, // pkg, title, actionUri
        allowedExternalPackages: Set<String>,
        optInExternalAppSearch: Boolean,
        optInWebResults: Boolean,
        privacySearchTemplate: String = "https://duckduckgo.com/?q=%s",
    ): List<FederatedHit> {
        val q = rawQuery.trim()
        if (q.isEmpty()) return emptyList()
        val out = ArrayList<FederatedHit>()

        // 1. Local hits ALWAYS occupy the top partition (score >= 1000 offset)
        for ((idx, item) in localHits.sortedByDescending { it.second }.withIndex()) {
            out.add(FederatedHit("local:$idx", item.first, ResultTier.LOCAL_PRIMARY, 10_000 + item.second, "fusion://local/$idx"))
        }

        // 2. Opt-in AppSearch / AppFunctions external slices (score 500..999)
        if (optInExternalAppSearch) {
            for ((idx, ext) in externalAppCandidates.withIndex()) {
                val (pkg, title, uri) = ext
                if (pkg in allowedExternalPackages && title.contains(q, ignoreCase = true)) {
                    out.add(FederatedHit("appfn:$pkg:$idx", title, ResultTier.EXTERNAL_APP_FUNCTION, 500 - idx, uri))
                }
            }
        }

        // 3. Opt-in web query link strictly at the bottom (score = 1)
        if (optInWebResults) {
            require(privacySearchTemplate.startsWith("https://")) { "web search template must use HTTPS" }
            val encoded = URLEncoder.encode(q, StandardCharsets.UTF_8)
            val url = privacySearchTemplate.replace("%s", encoded)
            out.add(FederatedHit("web:query", "Search web privately for \"$q\"", ResultTier.OPT_IN_WEB_FOOTER, 1, url))
        }
        return out
    }
}
