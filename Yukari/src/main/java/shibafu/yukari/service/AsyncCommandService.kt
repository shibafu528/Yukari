package shibafu.yukari.service

import android.app.IntentService
import android.content.Context
import android.content.Intent
import shibafu.yukari.BuildConfig
import shibafu.yukari.core.App
import shibafu.yukari.database.AuthUserRecord
import shibafu.yukari.database.Provider
import shibafu.yukari.entity.Status
import shibafu.yukari.twitter.TwitterApi
import shibafu.yukari.util.putErrorLog

/**
 * Created by shibafu on 2015/09/15.
 */
class AsyncCommandService : IntentService("AsyncCommandService") {
    override fun onHandleIntent(intent: Intent?) {
        if (intent == null) {
            putErrorLog("Intentがぬるぬるしてる \n" + intent.toString())
            if (BuildConfig.DEBUG) throw NullPointerException()
            return
        }

        //ActionとExtraの取得
        val action = intent.action
        val id = intent.getLongExtra(EXTRA_ID, -1)
        val targetStatus = intent.getSerializableExtra(EXTRA_TARGET_STATUS) as? Status
        val user = intent.getSerializableExtra(EXTRA_USER) as? AuthUserRecord

        if (action == null || (id < 0 && targetStatus == null) || user == null) {
            putErrorLog("Intentのパラメータが欠けてる \n" + intent.toString())
            if (BuildConfig.DEBUG) throw IllegalArgumentException("action: $action; id: $id; targetStatus: $targetStatus; user: $user")
            return
        }

        //処理を実行
        val app = App.getInstance(applicationContext)
        if (targetStatus != null) {
            val api = app.getProviderApi(user)
            if (api == null) {
                putErrorLog("ProviderApiがぬるぬるしてる \n" + intent.toString())
                if (BuildConfig.DEBUG) throw IllegalArgumentException("apiType: ${user.Provider.apiType}; user: $user")
                return
            }

            when (action) {
                ACTION_FAVORITE -> api.createFavorite(user, targetStatus)
                ACTION_UNFAVORITE -> api.destroyFavorite(user, targetStatus)
                ACTION_RETWEET -> api.repostStatus(user, targetStatus)
                ACTION_FAVRT -> {
                    api.createFavorite(user, targetStatus)
                    api.repostStatus(user, targetStatus)
                }
            }
        } else {
            if (user.Provider.apiType != Provider.API_TWITTER) {
                putErrorLog("Twitter Account以外での64bit ID依存APIコール \n" + intent.toString())
                if (BuildConfig.DEBUG) throw IllegalArgumentException("user: $user")
                return
            }

            val api = app.getProviderApi(user) as TwitterApi
            when (action) {
                ACTION_FAVORITE -> api.createFavorite(user, id)
            }
        }
    }

    companion object {
        private const val ACTION_FAVORITE = "FAVORITE"
        private const val ACTION_UNFAVORITE = "UNFAVORITE"
        private const val ACTION_RETWEET = "RETWEET"
        private const val ACTION_FAVRT = "FAVRT"

        private const val EXTRA_ID = "id"
        private const val EXTRA_USER = "user"
        private const val EXTRA_TARGET_STATUS = "targetStatus"

        @JvmStatic fun createFavorite(context: Context, id: Long, user: AuthUserRecord): Intent =
                Intent(context, AsyncCommandService::class.java).apply {
                    action = ACTION_FAVORITE
                    putExtra(EXTRA_ID, id)
                    putExtra(EXTRA_USER, user)
                }

        @JvmStatic fun createFavorite(context: Context, status: Status, user: AuthUserRecord): Intent =
                Intent(context, AsyncCommandService::class.java).apply {
                    action = ACTION_FAVORITE
                    putExtra(EXTRA_TARGET_STATUS, status)
                    putExtra(EXTRA_USER, user)
                }

        @JvmStatic fun destroyFavorite(context: Context, status: Status, user: AuthUserRecord): Intent =
                Intent(context, AsyncCommandService::class.java).apply {
                    action = ACTION_UNFAVORITE
                    putExtra(EXTRA_TARGET_STATUS, status)
                    putExtra(EXTRA_USER, user)
                }

        @JvmStatic fun createRepost(context: Context, status: Status, user: AuthUserRecord): Intent =
                Intent(context, AsyncCommandService::class.java).apply {
                    action = ACTION_RETWEET
                    putExtra(EXTRA_TARGET_STATUS, status)
                    putExtra(EXTRA_USER, user)
                }

        @JvmStatic fun createFavAndRepost(context: Context, status: Status, user: AuthUserRecord): Intent =
                Intent(context, AsyncCommandService::class.java).apply {
                    action = ACTION_FAVRT
                    putExtra(EXTRA_TARGET_STATUS, status)
                    putExtra(EXTRA_USER, user)
                }
    }
}
