package shibafu.yukari.twitter

import android.os.Handler
import android.os.Looper
import android.support.v4.util.LongSparseArray
import android.widget.Toast
import shibafu.yukari.database.Provider
import shibafu.yukari.entity.Status
import shibafu.yukari.linkage.ProviderApi
import shibafu.yukari.linkage.ProviderApiException
import shibafu.yukari.service.TwitterService
import shibafu.yukari.twitter.entity.TwitterStatus
import twitter4j.Twitter
import twitter4j.TwitterException
import twitter4j.TwitterFactory
import java.util.regex.Pattern

class TwitterApi : ProviderApi {
    private lateinit var service: TwitterService
    private lateinit var twitterFactory: TwitterFactory

    private val twitterInstances = LongSparseArray<Twitter>()

    override fun onCreate(service: TwitterService) {
        this.service = service
        this.twitterFactory = TwitterUtil.getTwitterFactory(service)
    }

    override fun onDestroy() {
        twitterInstances.clear()
    }

    override fun getApiClient(userRecord: AuthUserRecord?): Any? {
        if (userRecord == null) {
            return twitterFactory.instance
        }

        if (userRecord.Provider.apiType != Provider.API_TWITTER) {
            return null
        }

        if (twitterInstances.indexOfKey(userRecord.NumericId) < 0) {
            twitterInstances.put(userRecord.NumericId, twitterFactory.getInstance(userRecord.twitterAccessToken))
        }
        return twitterInstances.get(userRecord.NumericId)
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

    override fun showStatus(userRecord: AuthUserRecord, url: String): Status {
        val twitter = service.getTwitter(userRecord) ?: throw IllegalStateException("Twitterとの通信の準備に失敗しました")
        try {
            val id = url.toLongOrNull()
            if (id != null) {
                return TwitterStatus(twitter.showStatus(id), userRecord)
            }

            val matcher = PATTERN_TWITTER.matcher(url)
            if (matcher.find()) {
                val extractId = matcher.group(1).toLongOrNull()
                if (extractId != null) {
                    return TwitterStatus(twitter.showStatus(extractId), userRecord)
                }
            }
            throw IllegalArgumentException("非対応URLです : $url")
        } catch (e: TwitterException) {
            throw ProviderApiException(cause = e)
        }
    }

    companion object {
        private val PATTERN_TWITTER = Pattern.compile("^https?://(?:www\\.)?(?:mobile\\.)?twitter\\.com/(?:#!/)?[0-9a-zA-Z_]{1,15}/status(?:es)?/([0-9]+)/?(?:\\?.+)?\$")
    }
}