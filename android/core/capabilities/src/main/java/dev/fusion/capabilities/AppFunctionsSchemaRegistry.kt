// SPDX-License-Identifier: Apache-2.0
package dev.fusion.capabilities

/**
 * Clean-room Android API 37 `AppFunctions` Typed Schema & Consent Capability Gate (`AU-03`).
 *
 * Exposes strictly non-destructive, typed Fusion Shell functions (`notes.create`, `find.search`,
 * `workspace.switch`, `calc.evaluate`) with schema validation, sensitivity classification, and
 * mandatory explicit user-consent confirmation for state-mutating functions (Vault/Privacy functions
 * are NEVER exposed to external callers).
 */
object AppFunctionsSchemaRegistry {
    enum class ParamType { STRING, INT, BOOLEAN }

    data class ParamSpec(
        val name: String,
        val type: ParamType,
        val required: Boolean = true,
        val maxLength: Int = 512,
    )

    data class FunctionSchema(
        val functionId: String,
        val summary: String,
        val mutatesUserState: Boolean,
        val requiresExplicitUserPrompt: Boolean,
        val params: List<ParamSpec>,
    ) {
        init {
            require(!functionId.startsWith("vault.") && !functionId.startsWith("privacy.")) {
                "Vault and Privacy internals are forbidden from AppFunctions exposure"
            }
            if (mutatesUserState) {
                require(requiresExplicitUserPrompt) {
                    "state-mutating AppFunction '$functionId' must require explicit user prompt"
                }
            }
        }
    }

    val BUILTIN_SCHEMAS: Map<String, FunctionSchema> = listOf(
        FunctionSchema(
            "dev.fusion.calc.evaluate",
            "Evaluate a bounded deterministic decimal or scientific expression.",
            mutatesUserState = false,
            requiresExplicitUserPrompt = false,
            params = listOf(ParamSpec("expression", ParamType.STRING, required = true, maxLength = 256)),
        ),
        FunctionSchema(
            "dev.fusion.find.searchActions",
            "Query local window, workspace, and module commands (never files or Vault).",
            mutatesUserState = false,
            requiresExplicitUserPrompt = false,
            params = listOf(
                ParamSpec("query", ParamType.STRING, required = true, maxLength = 128),
                ParamSpec("limit", ParamType.INT, required = false),
            ),
        ),
        FunctionSchema(
            "dev.fusion.notes.createDraft",
            "Stage a new plain Markdown note draft after user confirmation.",
            mutatesUserState = true,
            requiresExplicitUserPrompt = true,
            params = listOf(
                ParamSpec("title", ParamType.STRING, required = true, maxLength = 120),
                ParamSpec("body", ParamType.STRING, required = true, maxLength = 4096),
            ),
        ),
        FunctionSchema(
            "dev.fusion.workspace.switchActive",
            "Switch active desktop workspace index after user confirmation.",
            mutatesUserState = true,
            requiresExplicitUserPrompt = true,
            params = listOf(ParamSpec("workspaceIndex", ParamType.INT, required = true)),
        ),
    ).associateBy { it.functionId }

    data class ValidationOutcome(
        val accepted: Boolean,
        val reason: String,
    )

    fun validateCall(
        functionId: String,
        args: Map<String, Any?>,
        callerHasUserConsentToken: Boolean,
        schemas: Map<String, FunctionSchema> = BUILTIN_SCHEMAS,
    ): ValidationOutcome {
        val schema = schemas[functionId]
            ?: return ValidationOutcome(false, "AppFunction '$functionId' is unregistered or denied")
        if (schema.requiresExplicitUserPrompt && !callerHasUserConsentToken) {
            return ValidationOutcome(false, "AppFunction '$functionId' requires explicit user consent token")
        }
        val allowedNames = schema.params.map { it.name }.toSet()
        val unknown = args.keys - allowedNames
        if (unknown.isNotEmpty()) {
            return ValidationOutcome(false, "unexpected parameters: ${unknown.sorted().joinToString()}")
        }
        for (p in schema.params) {
            val v = args[p.name]
            if (v == null) {
                if (p.required) return ValidationOutcome(false, "missing required parameter '${p.name}'")
                continue
            }
            when (p.type) {
                ParamType.STRING -> {
                    if (v !is String) return ValidationOutcome(false, "parameter '${p.name}' expects STRING")
                    if (v.length > p.maxLength) return ValidationOutcome(false, "parameter '${p.name}' exceeds ${p.maxLength} chars")
                }
                ParamType.INT -> if (v !is Int) return ValidationOutcome(false, "parameter '${p.name}' expects INT")
                ParamType.BOOLEAN -> if (v !is Boolean) return ValidationOutcome(false, "parameter '${p.name}' expects BOOLEAN")
            }
        }
        return ValidationOutcome(true, "validated")
    }
}
