package info.shibafu528.yukari.api.mastodon.ws

import java.net.URLEncoder

internal object UrlUtil {
    /**
     * 接続に使用するURLを生成します。
     *
     * [API Document](https://docs.joinmastodon.org/methods/streaming/#websocket)
     * @param serverUrl 接続先サーバのWebSocket URL。
     * @param accessToken アクセストークン。
     * @param stream 購読するストリームの名前。省略した場合は多重化セッションのためのURLを生成。
     */
    fun makeEndpointUrl(serverUrl: String, accessToken: String? = null, stream: String? = null): String {
        return buildString {
            append("$serverUrl/api/v1/streaming")

            val parameters = arrayListOf<Pair<String, String>>()
            if (accessToken != null) {
                parameters.add("access_token" to accessToken)
            }
            if (stream != null) {
                parameters.add("stream" to stream)
            }
            if (parameters.isNotEmpty()) {
                append("?")
                parameters.forEachIndexed { index, (key, value) ->
                    if (index != 0) {
                        append("&")
                    }
                    append(URLEncoder.encode(key, "UTF-8"))
                    append("=")
                    append(URLEncoder.encode(value, "UTF-8"))
                }
            }
        }
    }
}