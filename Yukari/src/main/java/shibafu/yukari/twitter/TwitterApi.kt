package shibafu.yukari.twitter

import android.os.Handler
import android.os.Looper
import android.preference.PreferenceManager
import android.support.v4.util.LongSparseArray
import android.widget.Toast
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import shibafu.yukari.database.Provider
import shibafu.yukari.entity.Status
import shibafu.yukari.entity.StatusDraft
import shibafu.yukari.linkage.PostValidator
import shibafu.yukari.linkage.ProviderApi
import shibafu.yukari.linkage.ProviderApiException
import shibafu.yukari.service.TwitterService
import shibafu.yukari.twitter.entity.TwitterMessage
import shibafu.yukari.twitter.entity.TwitterStatus
import twitter4j.CursorSupport
import twitter4j.StatusUpdate
import twitter4j.Twitter
import twitter4j.TwitterAPIConfiguration
import twitter4j.TwitterException
import twitter4j.TwitterFactory
import twitter4j.UploadedMedia
import java.io.File
import java.util.regex.Pattern

class TwitterApi : ProviderApi {
    var apiConfiguration: TwitterAPIConfiguration? = null
        private set

    private lateinit var service: TwitterService
    private lateinit var twitterFactory: TwitterFactory

    private val twitterInstances = LongSparseArray<Twitter>()

    override fun onCreate(service: TwitterService) {
        this.service = service
        this.twitterFactory = TwitterUtil.getTwitterFactory(service)

        GlobalScope.launch {
            // API Configurationの取得
            val primaryAccount = service.users.firstOrNull { it.isPrimary && it.Provider.apiType == Provider.API_TWITTER }
                    ?: service.users.firstOrNull { it.Provider.apiType == Provider.API_TWITTER }
            if (primaryAccount != null) {
                try {
                    apiConfiguration = (getApiClient(primaryAccount) as Twitter).apiConfiguration
                } catch (e: TwitterException) {
                    e.printStackTrace()
                }
            }

            // Blocks, Mutes, No-Retweetsの取得
            val twitterAccounts = service.users.filter { it.Provider.apiType == Provider.API_TWITTER }
            val suppressor = service.suppressor
            val sp = PreferenceManager.getDefaultSharedPreferences(service)
            if (sp.getBoolean("pref_filter_official", true) && suppressor != null) {
                twitterAccounts.forEach { userRecord ->
                    val twitter = getApiClient(userRecord) as? Twitter ?: return@forEach

                    try {
                        forEachCursor(twitter::getBlocksIDs) {
                            suppressor.addBlockedIDs(it.iDs)
                        }
                    } catch (ignored: TwitterException) {}

                    try {
                        forEachCursor(twitter::getMutesIDs) {
                            suppressor.addMutedIDs(it.iDs)
                        }
                    } catch (ignored: TwitterException) {}

                    try {
                        suppressor.addNoRetweetIDs(twitter.noRetweetsFriendships.iDs)
                    } catch (ignored: TwitterException) {}
                }
            }
        }
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

    override fun getPostValidator(userRecord: AuthUserRecord): PostValidator {
        return TweetValidator()
    }

    override fun getAccountUrl(userRecord: AuthUserRecord): String {
        return TwitterUtil.getProfileUrl(userRecord.ScreenName)
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

    override fun postStatus(userRecord: AuthUserRecord, draft: StatusDraft, mediaList: List<File>): Status {
        val twitter = service.getTwitter(userRecord) ?: throw IllegalStateException("Twitterとの通信の準備に失敗しました")
        try {
            if (draft.isDirectMessage) {
                val inReplyTo = draft.inReplyTo?.url?.toLongOrNull() ?: throw ProviderApiException("返信先に不正な値が指定されました。")
                val users = twitter.lookupUsers(inReplyTo, userRecord.NumericId)
                val result = twitter.sendDirectMessage(inReplyTo, draft.text)
                return TwitterMessage(result,
                        users.first { it.id == userRecord.NumericId },
                        users.first { it.id == inReplyTo },
                        userRecord)
            } else {
                var text = draft.text
                var attachmentUrl = ""

                // 画像添付なしで、ツイートを引用している場合は1つだけattachmentUrlに移動させる
                if (mediaList.isEmpty()) {
                    val matcher = PATTERN_TWITTER.matcher(text)
                    if (matcher.find()) {
                        attachmentUrl = matcher.group().trim()
                        text = matcher.replaceAll(text)
                    }
                }

                val update = StatusUpdate(text)
                if (attachmentUrl.isNotEmpty()) {
                    update.attachmentUrl = attachmentUrl
                }

                // 返信先URLが設定されている場合はin-reply-toに設定する
                val inReplyTo = draft.inReplyTo
                if (inReplyTo != null) {
                    val inReplyToId = TwitterUtil.getStatusIdFromUrl(inReplyTo.url)
                    if (inReplyToId > -1) {
                        update.inReplyToStatusId = inReplyToId
                    }
                }

                // 添付メディアのアップロード
                val mediaIds = mediaList.map { file ->
                    uploadMedia(userRecord, file).mediaId
                }
                update.setMediaIds(*mediaIds.toLongArray())

                // フラグ付きメディアの設定
                update.isPossiblySensitive = draft.isPossiblySensitive

                val result = twitter.updateStatus(update)
                val status = TwitterStatus(result, userRecord)

                service.timelineHub?.onStatus("Twitter.PostStatus", status, true)

                return status
            }
        } catch (e: TwitterException) {
            throw ProviderApiException("${e.errorCode} ${e.errorMessage}", e)
        }
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

            service.timelineHub?.onDelete(TwitterStatus::class.java, status.id)

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

    private fun uploadMedia(userRecord: AuthUserRecord, file: File): UploadedMedia {
        val twitter = service.getTwitter(userRecord) ?: throw IllegalStateException("Twitterとの通信の準備に失敗しました")
        return twitter.uploadMedia(file)
    }

    private inline fun <T : CursorSupport> forEachCursor(cursorSupport: (Long) -> T, action: (T) -> Unit) {
        var cursor = -1L
        do {
            val cs = cursorSupport(cursor)
            action(cs)
            cursor = cs.nextCursor
        } while (cs.hasNext())
    }

    companion object {
        private val PATTERN_TWITTER = Pattern.compile("^https?://(?:www\\.)?(?:mobile\\.)?twitter\\.com/(?:#!/)?[0-9a-zA-Z_]{1,15}/status(?:es)?/([0-9]+)/?(?:\\?.+)?\$")
    }
}