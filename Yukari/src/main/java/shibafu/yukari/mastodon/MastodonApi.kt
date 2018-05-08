package shibafu.yukari.mastodon

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.google.gson.Gson
import com.sys1yagi.mastodon4j.MastodonClient
import com.sys1yagi.mastodon4j.api.exception.Mastodon4jRequestException
import com.sys1yagi.mastodon4j.api.method.Statuses
import okhttp3.OkHttpClient
import shibafu.yukari.entity.Status
import shibafu.yukari.linkage.ProviderApi
import shibafu.yukari.service.TwitterService
import shibafu.yukari.twitter.AuthUserRecord

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

    override fun createFavorite(userRecord: AuthUserRecord, status: Status): Boolean {
        val client = service.getApiClient(userRecord) as? MastodonClient ?: throw IllegalStateException("Mastodonとの通信の準備に失敗しました")
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
        val client = service.getApiClient(userRecord) as? MastodonClient ?: throw IllegalStateException("Mastodonとの通信の準備に失敗しました")
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

    override fun repostStatus(userRecord: AuthUserRecord, status: Status): Boolean {
        val client = service.getApiClient(userRecord) as? MastodonClient ?: throw IllegalStateException("Mastodonとの通信の準備に失敗しました")
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
        val client = service.getApiClient(userRecord) as? MastodonClient ?: throw IllegalStateException("Mastodonとの通信の準備に失敗しました")
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
}