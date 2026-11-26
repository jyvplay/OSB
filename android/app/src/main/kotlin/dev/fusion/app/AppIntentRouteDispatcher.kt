// SPDX-License-Identifier: Apache-2.0
package dev.fusion.app

/**
 * Production-grade app-level `osb://` deep-link intent dispatcher & route registry.
 */
class AppIntentRouteDispatcher private constructor() {
    companion object {
        fun create(): AppIntentRouteDispatcher = AppIntentRouteDispatcher()
    }

    private val routes = LinkedHashMap<String, String>()

    fun registerRoute(hostAndPath: String, moduleId: String) {
        require(hostAndPath.isNotEmpty() && moduleId.isNotEmpty()) { "empty route" }
        routes[hostAndPath.lowercase()] = moduleId
    }

    fun resolveModule(uri: String): String? {
        val clean = uri.trim()
        if (!clean.startsWith("osb://")) return null
        val rest = clean.removePrefix("osb://").substringBefore('?').lowercase()
        return routes[rest]
    }
}
