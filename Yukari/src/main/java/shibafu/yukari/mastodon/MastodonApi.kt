package shibafu.yukari.mastodon

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.google.gson.Gson
import com.sys1yagi.mastodon4j.MastodonClient
import com.sys1yagi.mastodon4j.api.entity.Attachment
import com.sys1yagi.mastodon4j.api.entity.Status.Visibility
import com.sys1yagi.mastodon4j.api.exception.Mastodon4jRequestException
import com.sys1yagi.mastodon4j.api.method.Media
import com.sys1yagi.mastodon4j.api.method.Public
import com.sys1yagi.mastodon4j.api.method.Statuses
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import shibafu.yukari.entity.Status
import shibafu.yukari.entity.StatusDraft
import shibafu.yukari.linkage.PostValidator
import shibafu.yukari.linkage.ProviderApi
import shibafu.yukari.linkage.ProviderApiException
import shibafu.yukari.mastodon.entity.DonStatus
import shibafu.yukari.service.TwitterService
import shibafu.yukari.twitter.AuthUserRecord
import java.io.File
import java.io.IOException

class MastodonApi : ProviderApi {
    private lateinit var service: TwitterService

    override fun onCreate(service: TwitterService) {
        this.service = service
    }

    override fun onDestroy() {

    }

    override fun getApiClient(userRecord: AuthUserRecord?): Any? {
        if (userRecord == null) {
            return null
        }

        return getApiClient(userRecord.Provider.host, userRecord.AccessToken)
    }

    fun getApiClient(instanceName: String, accessToken: String?): MastodonClient {
        var builder = MastodonClient.Builder(
                instanceName,
                OkHttpClient.Builder().addInterceptor(service.userAgentInterceptor),
                Gson())
        if (accessToken != null && accessToken.isNotEmpty()) {
            builder = builder.accessToken(accessToken).useStreamingApi()
        }
        return builder.build()
    }

    override fun getPostValidator(userRecord: AuthUserRecord): PostValidator {
        return MastodonValidator()
    }

    override fun createFavorite(userRecord: AuthUserRecord, status: Status): Boolean {
        val client = getApiClient(userRecord) as? MastodonClient ?: throw IllegalStateException("Mastodonとの通信の準備に失敗しました")
        try {
            val statuses = Statuses(client)
            statuses.postFavourite(status.id).execute()
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(service.applicationContext, "ふぁぼりました (@" + userRecord.ScreenName + ")", Toast.LENGTH_SHORT).show()
            }
            return true
        } catch (e: Mastodon4jRequestException) {
            e.printStackTrace()
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(service.applicationContext, "ふぁぼれませんでした (@" + userRecord.ScreenName + ")", Toast.LENGTH_SHORT).show()
            }
        }
        return false
    }

    override fun destroyFavorite(userRecord: AuthUserRecord, status: Status): Boolean {
        val client = getApiClient(userRecord) as? MastodonClient ?: throw IllegalStateException("Mastodonとの通信の準備に失敗しました")
        try {
            val statuses = Statuses(client)
            statuses.postUnfavourite(status.id).execute()
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(service.applicationContext, "あんふぁぼしました (@" + userRecord.ScreenName + ")", Toast.LENGTH_SHORT).show()
            }
            return true
        } catch (e: Mastodon4jRequestException) {
            e.printStackTrace()
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(service.applicationContext, "あんふぁぼに失敗しました (@" + userRecord.ScreenName + ")", Toast.LENGTH_SHORT).show()
            }
        }
        return false
    }

    override fun postStatus(userRecord: AuthUserRecord, draft: StatusDraft, mediaList: List<File>): Status {
        val client = getApiClient(userRecord) as? MastodonClient ?: throw IllegalStateException("Mastodonとの通信の準備に失敗しました")

        try {
            // 添付メディアのアップロード
            val mediaIds = mediaList.map { file ->
                uploadMedia(userRecord, file).id
            }

            val visibility = when (draft.visibility) {
                StatusDraft.Visibility.PUBLIC -> Visibility.Public
                StatusDraft.Visibility.UNLISTED -> Visibility.Unlisted
                StatusDraft.Visibility.PRIVATE -> Visibility.Private
                StatusDraft.Visibility.DIRECT -> Visibility.Direct
            }

            // 返信先URLが設定されている場合は対象のインスタンスローカルなIDを取得
            val inReplyToUrl = draft.inReplyTo
            val inReplyToId = if (inReplyToUrl != null && inReplyToUrl.isNotEmpty()) {
                showStatus(userRecord, inReplyToUrl).id
            } else {
                null
            }

            val statuses = Statuses(client)
            val result = statuses.postStatus(
                    draft.text,
                    inReplyToId,
                    mediaIds,
                    draft.isPossiblySensitive,
                    null,
                    visibility
            ).execute()
            return DonStatus(result, userRecord)
        } catch (e: Mastodon4jRequestException) {
            val response = e.response
            if (response != null) {
                try {
                    val responseBody = response.body()?.string()
                    throw ProviderApiException("${response.code()} $responseBody", e)
                } catch (e: IOException) {
                    throw ProviderApiException("${response.code()} Unknown error", e)
                }
            } else {
                throw ProviderApiException("Unknown error", e)
            }
        }
    }

    override fun repostStatus(userRecord: AuthUserRecord, status: Status): Boolean {
        val client = getApiClient(userRecord) as? MastodonClient ?: throw IllegalStateException("Mastodonとの通信の準備に失敗しました")
        try {
            val statuses = Statuses(client)
            statuses.postReblog(status.id).execute()
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(service.applicationContext, "ブーストしました (@" + userRecord.ScreenName + ")", Toast.LENGTH_SHORT).show()
            }
            return true
        } catch (e: Mastodon4jRequestException) {
            e.printStackTrace()
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(service.applicationContext, "ブーストに失敗しました (@" + userRecord.ScreenName + ")", Toast.LENGTH_SHORT).show()
            }
        }
        return false
    }

    override fun destroyStatus(userRecord: AuthUserRecord, status: Status): Boolean {
        val client = getApiClient(userRecord) as? MastodonClient ?: throw IllegalStateException("Mastodonとの通信の準備に失敗しました")
        try {
            val statuses = Statuses(client)
            statuses.deleteStatus(status.id)
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(service.applicationContext, "トゥートを削除しました", Toast.LENGTH_SHORT).show()
            }
            return true
        } catch (e: Mastodon4jRequestException) {
            e.printStackTrace()
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(service.applicationContext, "トゥート削除に失敗しました", Toast.LENGTH_SHORT).show()
            }
        }
        return false
    }

    override fun showStatus(userRecord: AuthUserRecord, url: String): Status {
        val client = getApiClient(userRecord) as? MastodonClient ?: throw IllegalStateException("Mastodonとの通信の準備に失敗しました")
        try {
            val public = Public(client)
            val searchResult = public.getSearch(url, true).execute()
            val status = searchResult.statuses.firstOrNull() ?: throw ProviderApiException("Status not found. $url")
            return DonStatus(status, userRecord)
        } catch (e: Mastodon4jRequestException) {
            throw ProviderApiException(cause = e)
        }
    }

    @Throws(Mastodon4jRequestException::class)
    private fun uploadMedia(userRecord: AuthUserRecord, file: File): Attachment {
        val client = getApiClient(userRecord) as? MastodonClient ?: throw IllegalStateException("Mastodonとの通信の準備に失敗しました")
        val media = Media(client)
        return media.postMedia(
                MultipartBody.Part.createFormData(
                        "file", file.name,
                        RequestBody.create(MediaType.parse("image/png"), file)
                )
        ).execute()
    }
}