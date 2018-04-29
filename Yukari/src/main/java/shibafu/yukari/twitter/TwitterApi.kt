package shibafu.yukari.twitter

import shibafu.yukari.entity.Status
import shibafu.yukari.linkage.ProviderApi
import shibafu.yukari.service.TwitterService
import shibafu.yukari.util.showToast
import twitter4j.TwitterException

class TwitterApi : ProviderApi {
    private lateinit var service: TwitterService

    override fun onCreate(service: TwitterService) {
        this.service = service
    }

    override fun onDestroy() {

    }

    override fun createFavorite(userRecord: AuthUserRecord, status: Status) {
        val twitter = service.getTwitter(userRecord) ?: throw IllegalStateException("Twitterとの通信の準備に失敗しました")
        try {
            twitter.createFavorite(status.id)
            service.showToast("ふぁぼりました (@" + userRecord.ScreenName + ")")
        } catch (e: TwitterException) {
            e.printStackTrace()
            service.showToast("ふぁぼれませんでした (@" + userRecord.ScreenName + ")")
        }
    }

    override fun destroyFavorite(userRecord: AuthUserRecord, status: Status) {
        val twitter = service.getTwitter(userRecord) ?: throw IllegalStateException("Twitterとの通信の準備に失敗しました")
        try {
            twitter.destroyFavorite(status.id)
            service.showToast("あんふぁぼしました (@" + userRecord.ScreenName + ")")
        } catch (e: TwitterException) {
            e.printStackTrace()
            service.showToast("あんふぁぼに失敗しました (@" + userRecord.ScreenName + ")")
        }
    }
}