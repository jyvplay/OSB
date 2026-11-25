// SPDX-License-Identifier: Apache-2.0
package dev.fusion.beam

/**
 * Bounded non-privileged TCP/UDP port validator [1024, 65535].
 */
class BeamPeerPortValidator private constructor() {
    companion object {
        fun isValidPort(port: Int): Boolean = port in 1024..65535
    }
}
