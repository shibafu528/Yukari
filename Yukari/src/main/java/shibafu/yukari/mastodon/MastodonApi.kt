package shibafu.yukari.mastodon

import android.net.Uri
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
import okhttp3.*
import shibafu.yukari.database.Provider
import shibafu.yukari.entity.ShadowUser
import shibafu.yukari.entity.Status
import shibafu.yukari.entity.StatusDraft
import shibafu.yukari.linkage.PostValidator
import shibafu.yukari.linkage.ProviderApi
import shibafu.yukari.linkage.ProviderApiException
import shibafu.yukari.mastodon.entity.DonStatus
import shibafu.yukari.service.TwitterService
import shibafu.yukari.database.AuthUserRecord
import shibafu.yukari.entity.InReplyToId
import shibafu.yukari.util.defaultSharedPreferences
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
        val okHttpBuilder = OkHttpClient.Builder().addInterceptor(service.userAgentInterceptor)
        if (service.defaultSharedPreferences.getBoolean("pref_force_http1", false)) {
            okHttpBuilder.protocols(listOf(Protocol.HTTP_1_1))
        }
        var builder = MastodonClient.Builder(instanceName, okHttpBuilder, Gson())
        if (accessToken != null && accessToken.isNotEmpty()) {
            builder = builder.accessToken(accessToken).useStreamingApi()
        }
        return builder.build()
    }

    override fun getPostValidator(userRecord: AuthUserRecord): PostValidator {
        return MastodonValidator()
    }

    override fun getAccountUrl(userRecord: AuthUserRecord): String {
        val (screenName, _) = MastodonUtil.splitFullScreenName(userRecord.ScreenName)
        return "https://${userRecord.Provider.host}/@$screenName"
    }

    override fun createFavorite(userRecord: AuthUserRecord, status: Status): Boolean {
        val client = getApiClient(userRecord) as? MastodonClient ?: throw IllegalStateException("Mastodonとの通信の準備に失敗しました")
        try {
            val statuses = Statuses(client)
            val localId = resolveLocalId(userRecord, status as DonStatus) ?: throw ProviderApiException("IDが分かりません : ${status.url}")
            val favoritedStatus = statuses.postFavourite(localId).execute()
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(service.applicationContext, "ふぁぼりました (@" + userRecord.ScreenName + ")", Toast.LENGTH_SHORT).show()
            }

            service.timelineHub?.onFavorite(ShadowUser(userRecord), DonStatus(favoritedStatus, userRecord))

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
            val localId = resolveLocalId(userRecord, status as DonStatus) ?: throw ProviderApiException("IDが分かりません : ${status.url}")
            val unfavoritedStatus = statuses.postUnfavourite(localId).execute()
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(service.applicationContext, "あんふぁぼしました (@" + userRecord.ScreenName + ")", Toast.LENGTH_SHORT).show()
            }

            service.timelineHub?.onUnfavorite(ShadowUser(userRecord), DonStatus(unfavoritedStatus, userRecord))

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

            // 返信先が設定されている場合、対象のインスタンスローカルなIDが埋め込まれていればそのIDを使用する
            // インスタンスローカルなIDが未知の場合は、サーバからの取得を試みる
            val inReplyToId = resolveInReplyToId(userRecord, draft.inReplyTo)

            val statuses = Statuses(client)
            val result = statuses.postStatus(
                    draft.text,
                    inReplyToId,
                    mediaIds,
                    draft.isPossiblySensitive,
                    draft.spoilerText,
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
            val localId = resolveLocalId(userRecord, status as DonStatus) ?: throw ProviderApiException("IDが分かりません : ${status.url}")
            statuses.postReblog(localId).execute()
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
            val localId = resolveLocalId(userRecord, status as DonStatus) ?: throw ProviderApiException("IDが分かりません : ${status.url}")
            statuses.deleteStatus(localId)
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

    /**
     * 可能な範囲で [status] のインスタンスローカルなIDを取得します。
     * @param userRecord 操作アカウント
     * @param status IDを取得したい [Status]
     * @return インスタンスローカルID、取得に失敗した場合は -1
     * @throws Mastodon4jRequestException サーバへのリクエストを試行し、失敗した場合にスロー
     */
    @Throws(Mastodon4jRequestException::class)
    private fun resolveLocalId(userRecord: AuthUserRecord, status: DonStatus): Long? {
        status.checkProviderHostMismatching()
        if (status.providerHost == userRecord.Provider.host) {
            return status.id
        }

        val perProviderId = status.perProviderId.getIfAbsent(userRecord.Provider.host, -1L)
        if (perProviderId != -1L) {
            return perProviderId
        }

        val client = getApiClient(userRecord) as? MastodonClient ?: return -1
        val public = Public(client)
        val searchResult = public.getSearch(status.url, true).execute()
        val localStatus = searchResult.statuses.firstOrNull() ?: return -1
        return localStatus.id
    }

    /**
     * 返信先StatusのインスタンスローカルなIDを取得します。
     * @param userRecord 操作アカウント
     * @param inReplyTo 下書きのInReplyTo情報
     * @return インスタンスローカルな返信先のStatus ID、取得に失敗した場合はnull
     */
    private fun resolveInReplyToId(userRecord: AuthUserRecord, inReplyTo: InReplyToId?): Long? {
        if (inReplyTo == null) {
            return null
        }

        // 有効な既知のIDがあれば使う
        inReplyTo[Provider.API_MASTODON, userRecord.Provider.host]?.toLong()?.let {
            return it
        }

        // TwitterのURLの場合、どうやっても解決できないので無かったことにする
        val host = Uri.parse(inReplyTo.url).host
        if (host != null && host.endsWith("twitter.com")) {
            return null
        }

        // サーバに問い合わせる
        return showStatus(userRecord, inReplyTo.url).id
    }
}