package shibafu.yukari.twitter

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.preference.PreferenceManager
import androidx.collection.LongSparseArray
import android.widget.Toast
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import shibafu.yukari.common.Suppressor
import shibafu.yukari.core.App
import shibafu.yukari.database.AccountManager
import shibafu.yukari.database.AuthUserRecord
import shibafu.yukari.database.Provider
import shibafu.yukari.entity.ShadowUser
import shibafu.yukari.entity.Status
import shibafu.yukari.entity.StatusDraft
import shibafu.yukari.linkage.PostValidator
import shibafu.yukari.linkage.ProviderApi
import shibafu.yukari.linkage.ProviderApiException
import shibafu.yukari.linkage.TimelineHub
import shibafu.yukari.twitter.entity.TwitterMessage
import shibafu.yukari.twitter.entity.TwitterStatus
import shibafu.yukari.util.putDebugLog
import twitter4j.CursorSupport
import twitter4j.StatusUpdate
import twitter4j.Twitter
import twitter4j.TwitterException
import twitter4j.TwitterFactory
import twitter4j.UploadedMedia
import java.io.File
import java.util.regex.Pattern

class TwitterApi : ProviderApi {
    private lateinit var context: Context
    private lateinit var accountManager: AccountManager
    private lateinit var suppressor: Suppressor
    private lateinit var timelineHub: TimelineHub
    private lateinit var twitterProvider: TwitterProvider
    private lateinit var twitterFactory: TwitterFactory

    private val twitterInstances = LongSparseArray<Twitter>()

    override fun onCreate(context: Context) {
        val app = App.getInstance(context)
        this.context = app
        this.accountManager = app.accountManager
        this.suppressor = app.suppressor
        this.timelineHub = app.timelineHub
        this.twitterProvider = app
        this.twitterFactory = TwitterUtil.getTwitterFactory(app)
    }

    override fun getApiClient(userRecord: AuthUserRecord?): Any? {
        if (userRecord == null) {
            return twitterFactory.instance
        }

        if (userRecord.Provider.apiType != Provider.API_TWITTER) {
            return null
        }

        return synchronized(twitterInstances) {
            if (twitterInstances.indexOfKey(userRecord.NumericId) < 0) {
                twitterInstances.put(userRecord.NumericId, twitterFactory.getInstance(userRecord.twitterAccessToken))
            }

            twitterInstances.get(userRecord.NumericId).also {
                if (it?.oAuthAccessToken?.userId != userRecord.NumericId) {
                    throw InstanceCacheBrokenException()
                }
            }
        }
    }

    override fun getPostValidator(userRecord: AuthUserRecord): PostValidator {
        return TweetValidator()
    }

    override fun getAccountUrl(userRecord: AuthUserRecord): String {
        return TwitterUtil.getProfileUrl(userRecord.ScreenName)
    }

    // 互換用
    fun createFavorite(userRecord: AuthUserRecord, id: Long): Boolean {
        val twitter = twitterProvider.getTwitter(userRecord) ?: throw IllegalStateException("Twitterとの通信の準備に失敗しました")
        try {
            val status = twitter.createFavorite(id)
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "ふぁぼりました (@" + userRecord.ScreenName + ")", Toast.LENGTH_SHORT).show()
            }

            timelineHub.onFavorite(ShadowUser(userRecord), TwitterStatus(status, userRecord))

            return true
        } catch (e: TwitterException) {
            e.printStackTrace()
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "ふぁぼれませんでした (@" + userRecord.ScreenName + ")", Toast.LENGTH_SHORT).show()
            }
        }
        return false
    }

    override fun createFavorite(userRecord: AuthUserRecord, status: Status): Boolean {
        val twitter = twitterProvider.getTwitter(userRecord) ?: throw IllegalStateException("Twitterとの通信の準備に失敗しました")
        try {
            val favoritedStatus = twitter.createFavorite(status.id)
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "ふぁぼりました (@" + userRecord.ScreenName + ")", Toast.LENGTH_SHORT).show()
            }

            timelineHub.onFavorite(ShadowUser(userRecord), TwitterStatus(favoritedStatus, userRecord))

            return true
        } catch (e: TwitterException) {
            e.printStackTrace()
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "ふぁぼれませんでした (@" + userRecord.ScreenName + ")", Toast.LENGTH_SHORT).show()
            }
        }
        return false
    }

    override fun destroyFavorite(userRecord: AuthUserRecord, status: Status): Boolean {
        val twitter = twitterProvider.getTwitter(userRecord) ?: throw IllegalStateException("Twitterとの通信の準備に失敗しました")
        try {
            val unfavoriteStatus = twitter.destroyFavorite(status.id)
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "あんふぁぼしました (@" + userRecord.ScreenName + ")", Toast.LENGTH_SHORT).show()
            }

            timelineHub.onUnfavorite(ShadowUser(userRecord), TwitterStatus(unfavoriteStatus, userRecord))

            return true
        } catch (e: TwitterException) {
            e.printStackTrace()
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "あんふぁぼに失敗しました (@" + userRecord.ScreenName + ")", Toast.LENGTH_SHORT).show()
            }
        }
        return false
    }

    override fun postStatus(userRecord: AuthUserRecord, draft: StatusDraft, mediaList: List<File>): Status {
        val twitter = twitterProvider.getTwitter(userRecord) ?: throw IllegalStateException("Twitterとの通信の準備に失敗しました")
        try {
            if (draft.isDirectMessage) {
                val inReplyTo = draft.inReplyTo?.url?.let { TwitterUtil.getUserIdFromUrl(it) } ?: -1
                if (inReplyTo == -1L) {
                    throw ProviderApiException("返信先に不正な値が指定されました。")
                }
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
                    val matcher = PATTERN_QUOTE.matcher(text)
                    if (matcher.find()) {
                        val matchUrl = matcher.group().trim()
                        val newText = matcher.replaceAll("")
                        // 本文が空になってしまうと投稿できないため、その場合はattachmentUrlに移動させない
                        if (newText.isNotEmpty()) {
                            attachmentUrl = matchUrl
                            text = newText
                        }
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

                putDebugLog(update.toString())

                val result = twitter.updateStatus(update)
                val status = TwitterStatus(result, userRecord)

                timelineHub.onStatus("Twitter.PostStatus", status, true)

                return status
            }
        } catch (e: TwitterException) {
            throw ProviderApiException("${e.errorCode} ${e.errorMessage}", e)
        }
    }

    override fun repostStatus(userRecord: AuthUserRecord, status: Status): Boolean {
        val twitter = twitterProvider.getTwitter(userRecord) ?: throw IllegalStateException("Twitterとの通信の準備に失敗しました")
        try {
            twitter.retweetStatus(status.id)
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "RTしました (@" + userRecord.ScreenName + ")", Toast.LENGTH_SHORT).show()
            }
            return true
        } catch (e: TwitterException) {
            e.printStackTrace()
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "RTに失敗しました (@" + userRecord.ScreenName + ")", Toast.LENGTH_SHORT).show()
            }
        }
        return false
    }

    override fun destroyStatus(userRecord: AuthUserRecord, status: Status): Boolean {
        val twitter = twitterProvider.getTwitter(userRecord) ?: throw IllegalStateException("Twitterとの通信の準備に失敗しました")
        try {
            twitter.destroyStatus(status.id)

            timelineHub.onDelete(Provider.TWITTER.host, status.id)

            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "ツイートを削除しました", Toast.LENGTH_SHORT).show()
            }
            return true
        } catch (e: TwitterException) {
            e.printStackTrace()
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "ツイート削除に失敗しました", Toast.LENGTH_SHORT).show()
            }
        }
        return false
    }

    override fun showStatus(userRecord: AuthUserRecord, url: String): Status {
        val twitter = twitterProvider.getTwitter(userRecord) ?: throw IllegalStateException("Twitterとの通信の準備に失敗しました")
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
        val twitter = twitterProvider.getTwitter(userRecord) ?: throw IllegalStateException("Twitterとの通信の準備に失敗しました")
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
        private val PATTERN_QUOTE = Pattern.compile("\\shttps?://(?:www\\.)?(?:mobile\\.)?twitter\\.com/(?:#!/)?[0-9a-zA-Z_]{1,15}/status(?:es)?/([0-9]+)/?(?:\\?.+)?\$")
    }

    class InstanceCacheBrokenException : Exception("TwitterインスタンスキャッシュのAccessTokenと、要求しているアカウントのUserIDが一致しません!")
}