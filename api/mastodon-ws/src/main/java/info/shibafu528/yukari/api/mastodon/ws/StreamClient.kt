package info.shibafu528.yukari.api.mastodon.ws

import com.google.gson.Gson
import okhttp3.*
import java.net.URLEncoder
import java.util.concurrent.CopyOnWriteArrayList

// TODO: サーバがMux非対応 (Mastodon < 3.3.0) の場合のフォールバック処理 (stream単位でws接続し、コネクションを管理し続ける必要がある)

/**
 * WebSocketでMastodonのstreamingサーバに接続し、各種イベントを購読することができます。
 */
class StreamClient private constructor(private val serverUrl: String,
                                       private val accessToken: String?,
                                       private val okHttpClient: OkHttpClient) {
    /**
     * カスタマイズされた [StreamClient] を作成するためのビルダークラスです。
     *
     * @property serverUrl 接続先サーバーのWebSocket URL。
     *                     通常はMastodonサーバのURLスキームをws/wssに変更したもの (例: wss://example.com) ですが、カスタマイズされている場合もあります。
     *                     決め打ちせずに `GET /api/v1/instance` で確認することが推奨されます。
     */
    class Builder(private val serverUrl: String,
                  private val okHttpClientBuilder: OkHttpClient.Builder) {
        private var accessToken: String? = null

        /**
         * 接続時に使用するアクセストークンを設定します。認証が必要なストリームを購読する場合には必須です。
         */
        fun accessToken(accessToken: String) = apply {
            this.accessToken = accessToken
        }

        /**
         * 与えられたオプションで [StreamClient] のインスタンスを生成します。
         */
        fun build(): StreamClient {
            return StreamClient(serverUrl, accessToken, okHttpClientBuilder.build())
        }
    }

    internal val gson = Gson()

    /**
     * 登録されている購読のリスト。
     */
    internal val subscriptions = CopyOnWriteArrayList<Subscription>()

    /**
     * 多重化セッション用のWebSocketイベントリスナー。
     */
    private val muxListener = MuxListener(this)

    /**
     * 多重化セッション用のWebSocketコネクション。サーバが対応している場合、このコネクションのみを使用して購読する。
     */
    private val muxConnection = AutoReconnectWebSocket(okHttpClient, Request.Builder().url(makeEndpointUrl()).build(), muxListener)

    /**
     * 指定されたストリームの購読を開始します。
     * @param subscription 購読に使用するパラメータ。
     */
    fun subscribe(subscription: Subscription) {
        muxConnection.send(subscription.toMessage("subscribe"))
        subscriptions.add(subscription)
    }

    /**
     * 指定されたストリームの購読を解除します。購読を開始した時と引数が一致している必要があります。
     * @param subscription 購読解除するストリームのパラメータ。
     */
    fun unsubscribe(subscription: Subscription) {
        muxConnection.send(subscription.toMessage("unsubscribe"))
        subscriptions.remove(subscription)
    }

    /**
     * 全ての購読を解除し、サーバとの通信を切断します。このメソッドの呼び出し以降、このインスタンスを再利用することはできません。
     */
    fun disconnect() {
        muxConnection.cancel()
    }

    internal fun onOpen(webSocket: WebSocket, response: Response) {
        System.err.println("StreamClient.onOpen: Connected.")
    }

    internal fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        System.err.println("StreamClient.onFailure: Disconnected.")
        t.printStackTrace()
    }

    internal inline fun forEachSubscriptionsByStream(stream: List<String>, action: (Subscription) -> Unit) {
        subscriptions.forEach { subscription ->
            if (stream.contains(subscription.stream)) {
                action(subscription)
            }
        }
    }

    /**
     * 接続に使用するURLを生成します。
     *
     * [API Document](https://docs.joinmastodon.org/methods/streaming/#websocket)
     * @param stream 購読するストリームの名前。省略した場合は多重化セッションのためのURLを生成。
     */
    private fun makeEndpointUrl(stream: String? = null): String {
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