package info.shibafu528.yukari.api.mastodon.ws

import okhttp3.OkHttpClient

interface StreamClient {
    /**
     * カスタマイズされた [MuxStreamClient] を作成するためのビルダークラスです。
     *
     * @property serverUrl 接続先サーバのWebSocket URL。
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
         * 与えられたオプションで [MuxStreamClient] のインスタンスを生成します。
         */
        fun build(): StreamClient {
            return MuxStreamClient(serverUrl, accessToken, okHttpClientBuilder.build())
        }
    }

    /**
     * 指定されたストリームの購読を開始します。
     * @param subscription 購読に使用するパラメータ。
     */
    fun subscribe(subscription: Subscription)

    /**
     * 指定されたストリームの購読を解除します。購読を開始した時と引数が一致している必要があります。
     * @param subscription 購読解除するストリームのパラメータ。
     */
    fun unsubscribe(subscription: Subscription)

    /**
     * 全ての購読を解除し、サーバとの通信を切断します。このメソッドの呼び出し以降、このインスタンスを再利用することはできません。
     */
    fun disconnect()
}