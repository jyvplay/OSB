package dev.fusion.castv2

/**
 * Minimal JSON payload builders for Default Media Receiver control.
 * No TLS transport or protobuf framing is implemented here.
 */
object CastJson {
    const val DEFAULT_MEDIA_RECEIVER = "CC1AD845"

    fun launch(requestId: Int, appId: String = DEFAULT_MEDIA_RECEIVER): String =
        "{\"type\":\"LAUNCH\",\"appId\":\"$appId\",\"requestId\":$requestId}"

    fun load(requestId: Int, url: String, contentType: String, title: String): String =
        "{\"type\":\"LOAD\",\"requestId\":$requestId," +
            "\"media\":{\"contentId\":\"$url\",\"contentType\":\"$contentType\"," +
            "\"streamType\":\"BUFFERED\",\"metadata\":{\"type\":0,\"title\":\"$title\"}}," +
            "\"autoplay\":true}"
}
