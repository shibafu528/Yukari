package shibafu.yukari.media2.impl

/**
 * 彼女の仕事場にケーキを差し入れして来た。
 * 初夏に彼女に出会ってから今まで知らなかった世界を知ったし、自分の足りないところを色々と教わった。
 * 働き者の彼女はこんな日も仕事だけど、支えてもらって本当に感謝してる。ありがとう。
 *
 * https://twitter.com/kamiya344/status/150588901919162368
 */
class RouterCake(private val browseURL: String) : D250g2("http://router-cake.d250g2.com") {
    // 元のURLで表示されるようにする
    override fun getBrowseUrl(): String = browseURL
    override fun toString(): String = browseURL

    companion object {
        const val ORIGIN_URL: String = "http://yfrog.com/es3bcstj"
    }
}