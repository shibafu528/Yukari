package info.shibafu528.yukari.api.mastodon.ws

import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket

/**
 * ストリームごとにWebSocket接続を作成する、Mastodon 3.3未満のstreamingサーバ向けのクライアントです。
 */
class LegacyStreamClient internal constructor(private val serverUrl: String,
                                              private val accessToken: String?,
                                              private val okHttpClient: OkHttpClient) : StreamClient {
    private val gson = Gson()

    /**
     * 登録されている購読のリスト。
     */
    private val subscriptions = ArrayList<Pair<Subscription, WebSocket>>()

    /**
     * 指定されたストリームの購読を開始します。
     * @param subscription 購読に使用するパラメータ。
     */
    @Synchronized
    override fun subscribe(subscription: Subscription) {
        val connection = AutoReconnectWebSocket(
            okHttpClient,
            Request.Builder().url(UrlUtil.makeEndpointUrl(serverUrl, accessToken, subscription.stream)).build(),
            LegacyListener(subscription, gson)
        )
        subscriptions.add(subscription to connection)
    }

    /**
     * 指定されたストリームの購読を解除します。購読を開始した時と引数が一致している必要があります。
     * @param subscription 購読解除するストリームのパラメータ。
     */
    @Synchronized
    override fun unsubscribe(subscription: Subscription) {
        val iterator = subscriptions.iterator()
        while (iterator.hasNext()) {
            val (sub, connection) = iterator.next()
            if (sub != subscription) {
                continue
            }

            connection.close(1000, null)
            iterator.remove()
        }
    }

    /**
     * 全ての購読を解除し、サーバとの通信を切断します。このメソッドの呼び出し以降、このインスタンスを再利用することはできません。
     */
    @Synchronized
    override fun disconnect() {
        subscriptions.forEach { it.second.close(1000, null) }
        subscriptions.clear()
    }
}