package shibafu.yukari.twitter

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import shibafu.yukari.entity.Status
import shibafu.yukari.linkage.ProviderApi
import shibafu.yukari.service.TwitterService
import twitter4j.TwitterException

class TwitterApi : ProviderApi {
    private lateinit var service: TwitterService

    override fun onCreate(service: TwitterService) {
        this.service = service
    }

    override fun onDestroy() {

    }

    // 互換用
    fun createFavorite(userRecord: AuthUserRecord, id: Long): Boolean {
        val twitter = service.getTwitter(userRecord) ?: throw IllegalStateException("Twitterとの通信の準備に失敗しました")
        try {
            twitter.createFavorite(id)
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(service.applicationContext, "ふぁぼりました (@" + userRecord.ScreenName + ")", Toast.LENGTH_SHORT).show()
            }
            return true
        } catch (e: TwitterException) {
            e.printStackTrace()
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(service.applicationContext, "ふぁぼれませんでした (@" + userRecord.ScreenName + ")", Toast.LENGTH_SHORT).show()
            }
        }
        return false
    }

    override fun createFavorite(userRecord: AuthUserRecord, status: Status): Boolean {
        val twitter = service.getTwitter(userRecord) ?: throw IllegalStateException("Twitterとの通信の準備に失敗しました")
        try {
            twitter.createFavorite(status.id)
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(service.applicationContext, "ふぁぼりました (@" + userRecord.ScreenName + ")", Toast.LENGTH_SHORT).show()
            }
            return true
        } catch (e: TwitterException) {
            e.printStackTrace()
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(service.applicationContext, "ふぁぼれませんでした (@" + userRecord.ScreenName + ")", Toast.LENGTH_SHORT).show()
            }
        }
        return false
    }

    override fun destroyFavorite(userRecord: AuthUserRecord, status: Status): Boolean {
        val twitter = service.getTwitter(userRecord) ?: throw IllegalStateException("Twitterとの通信の準備に失敗しました")
        try {
            twitter.destroyFavorite(status.id)
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(service.applicationContext, "あんふぁぼしました (@" + userRecord.ScreenName + ")", Toast.LENGTH_SHORT).show()
            }
            return true
        } catch (e: TwitterException) {
            e.printStackTrace()
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(service.applicationContext, "あんふぁぼに失敗しました (@" + userRecord.ScreenName + ")", Toast.LENGTH_SHORT).show()
            }
        }
        return false
    }

    override fun repostStatus(userRecord: AuthUserRecord, status: Status): Boolean {
        val twitter = service.getTwitter(userRecord) ?: throw IllegalStateException("Twitterとの通信の準備に失敗しました")
        try {
            twitter.retweetStatus(status.id)
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(service.applicationContext, "RTしました (@" + userRecord.ScreenName + ")", Toast.LENGTH_SHORT).show()
            }
            return true
        } catch (e: TwitterException) {
            e.printStackTrace()
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(service.applicationContext, "RTに失敗しました (@" + userRecord.ScreenName + ")", Toast.LENGTH_SHORT).show()
            }
        }
        return false
    }

    override fun destroyStatus(userRecord: AuthUserRecord, status: Status): Boolean {
        val twitter = service.getTwitter(userRecord) ?: throw IllegalStateException("Twitterとの通信の準備に失敗しました")
        try {
            twitter.destroyStatus(status.id)
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(service.applicationContext, "ツイートを削除しました", Toast.LENGTH_SHORT).show()
            }
            return true
        } catch (e: TwitterException) {
            e.printStackTrace()
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(service.applicationContext, "ツイート削除に失敗しました", Toast.LENGTH_SHORT).show()
            }
        }
        return false
    }
}