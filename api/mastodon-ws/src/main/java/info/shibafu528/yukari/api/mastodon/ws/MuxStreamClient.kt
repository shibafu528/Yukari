package info.shibafu528.yukari.api.mastodon.ws

import com.google.gson.Gson
import okhttp3.*
import java.util.concurrent.CopyOnWriteArrayList

/**
 * WebSocketでMastodonのstreamingサーバに接続し、各種イベントを購読することができます。
 */
class MuxStreamClient internal constructor(private val serverUrl: String,
                                           private val accessToken: String?,
                                           private val okHttpClient: OkHttpClient) : StreamClient {
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
    private val muxConnection = AutoReconnectWebSocket(okHttpClient, Request.Builder().url(UrlUtil.makeEndpointUrl(serverUrl, accessToken)).build(), muxListener)

    /**
     * 次にWebSocketコネクションを確立した時に購読を再送する必要があるかどうか。購読を試みた時に接続が確立できていなかった場合や、一時的に切断されてしまった場合に使う。
     */
    private var needResubscribeOnOpen = false

    /**
     * 指定されたストリームの購読を開始します。
     * @param subscription 購読に使用するパラメータ。
     */
    override fun subscribe(subscription: Subscription) {
        if (!muxConnection.send(subscription.toMessage("subscribe"))) {
            needResubscribeOnOpen = true
        }
        subscriptions.add(subscription)
    }

    /**
     * 指定されたストリームの購読を解除します。購読を開始した時と引数が一致している必要があります。
     * @param subscription 購読解除するストリームのパラメータ。
     */
    override fun unsubscribe(subscription: Subscription) {
        muxConnection.send(subscription.toMessage("unsubscribe"))
        subscriptions.remove(subscription)
    }

    /**
     * 全ての購読を解除し、サーバとの通信を切断します。このメソッドの呼び出し以降、このインスタンスを再利用することはできません。
     */
    override fun disconnect() {
        muxConnection.close(1000, null)
    }

    internal fun onOpen(webSocket: WebSocket, response: Response) {
        System.err.println("MuxStreamClient.onOpen: Connected.")
        // 再購読
        if (needResubscribeOnOpen) {
            subscriptions.forEach { subscription ->
                System.err.println("MuxStreamClient.onOpen: subscribe ${subscription.stream}")
                muxConnection.send(subscription.toMessage("subscribe"))
            }
        }
    }

    internal fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        System.err.println("MuxStreamClient.onFailure: Disconnected.")
        t.printStackTrace()
        needResubscribeOnOpen = true
    }

    internal inline fun forEachSubscriptionsByStream(stream: List<String>, action: (Subscription) -> Unit) {
        subscriptions.forEach { subscription ->
            if (stream.contains(subscription.stream)) {
                action(subscription)
            }
        }
    }
}