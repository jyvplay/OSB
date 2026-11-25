// SPDX-License-Identifier: Apache-2.0
package dev.fusion.airplay

/**
 * Bounded RTSP status code classifier.
 */
class AirPlayRtspStatusCode private constructor() {
    companion object {
        enum class Category { SUCCESS, AUTH_REQUIRED, CLIENT_ERROR, SERVER_ERROR, OTHER }

        fun classify(code: Int): Category {
            require(code in 100..599) { "invalid RTSP status code: $code" }
            return when {
                code in 200..299 -> Category.SUCCESS
                code == 401 || code == 403 -> Category.AUTH_REQUIRED
                code in 400..499 -> Category.CLIENT_ERROR
                code in 500..599 -> Category.SERVER_ERROR
                else -> Category.OTHER
            }
        }
    }
}
