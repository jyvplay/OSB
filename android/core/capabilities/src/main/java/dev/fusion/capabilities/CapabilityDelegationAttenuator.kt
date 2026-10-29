// SPDX-License-Identifier: Apache-2.0
package dev.fusion.capabilities

/**
 * Pure-JVM **Plugin Capability Manifest Attenuation Verifier** for `:core:capabilities`,
 * enforcing object-capability subset attenuation (`childPerms subseteq parentPerms`) when
 * spawning sandboxed Wasm media/document plugins!
 */
object CapabilityDelegationAttenuator {
    const val SCOPE =
        "Monotonic object-capability attenuation verifier guaranteeing delegated plugin tokens never amplify ambient permissions."

    data class CapabilityGrant(
        val allowedReadPrefixes: Set<String>,
        val maxMemoryMiB: Int,
        val maxFuelInstructions: Long,
        val allowOutboundNetwork: Boolean,
    )

    fun delegateAttenuated(parent: CapabilityGrant, requested: CapabilityGrant): Result<CapabilityGrant> {
        if (requested.allowOutboundNetwork && !parent.allowOutboundNetwork) {
            return Result.failure(SecurityException("CAPABILITY_AMPLIFICATION_NETWORK_DENIED"))
        }
        if (requested.maxMemoryMiB > parent.maxMemoryMiB || requested.maxFuelInstructions > parent.maxFuelInstructions) {
            return Result.failure(SecurityException("CAPABILITY_AMPLIFICATION_RESOURCE_QUOTA_DENIED"))
        }
        val unauthorizedPaths = requested.allowedReadPrefixes.filter { reqPrefix ->
            parent.allowedReadPrefixes.none { parentPrefix -> reqPrefix.startsWith(parentPrefix) }
        }
        if (unauthorizedPaths.isNotEmpty()) {
            return Result.failure(SecurityException("CAPABILITY_AMPLIFICATION_PATH_DENIED:$unauthorizedPaths"))
        }
        return Result.success(requested)
    }
}
