package shibafu.yukari.service

import android.app.IntentService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import shibafu.yukari.BuildConfig
import shibafu.yukari.database.Provider
import shibafu.yukari.entity.Status
import shibafu.yukari.twitter.AuthUserRecord
import shibafu.yukari.util.putDebugLog
import shibafu.yukari.util.putErrorLog

/**
 * Created by shibafu on 2015/09/15.
 */
class AsyncCommandService : IntentService("AsyncCommandService") {
    private var service: TwitterService? = null
    private var serviceBound: Boolean = false

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

        //サービスバインドまで待機
        while (!serviceBound) {
            try {
                Thread.sleep(100)
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }

        //処理を実行
        val service = service!!
        if (targetStatus != null) {
            val api = service.getProviderApi(user)
            if (api == null) {
                putErrorLog("ProviderApiがぬるぬるしてる \n" + intent.toString())
                if (BuildConfig.DEBUG) throw IllegalArgumentException("apiType: ${user.Provider.apiType}; user: $user")
                return
            }

            when (action) {
                ACTION_FAVORITE -> api.createFavorite(user, targetStatus)
                ACTION_UNFAVORITE -> api.destroyFavorite(user, targetStatus)
                ACTION_RETWEET -> api.repostStatus(user, targetStatus)
                ACTION_FAVRT -> TODO()
            }
        } else {
            if (user.Provider.apiType != Provider.API_TWITTER) {
                putErrorLog("Twitter Account以外での64bit ID依存APIコール \n" + intent.toString())
                if (BuildConfig.DEBUG) throw IllegalArgumentException("user: $user")
                return
            }

            when (action) {
                ACTION_FAVORITE -> service.createFavorite(user, id)
                ACTION_UNFAVORITE -> service.destroyFavorite(user, id)
                ACTION_RETWEET -> service.retweetStatus(user, id)
                ACTION_FAVRT -> {
                    service.createFavorite(user, id)
                    service.retweetStatus(user, id)
                }
            }
        }
    }

    companion object {
        private val ACTION_FAVORITE = "FAVORITE"
        private val ACTION_UNFAVORITE = "UNFAVORITE"
        private val ACTION_RETWEET = "RETWEET"
        private val ACTION_FAVRT = "FAVRT"

        private val EXTRA_ID = "id"
        private val EXTRA_USER = "user"
        private val EXTRA_TARGET_STATUS = "targetStatus"

        private fun createIntent(context: Context, action: String, id: Long, user: AuthUserRecord): Intent {
            val intent = Intent(context, AsyncCommandService::class.java)
            intent.setAction(action)
            intent.putExtra(EXTRA_ID, id)
            intent.putExtra(EXTRA_USER, user)
            return intent
        }

        @JvmStatic fun createFavorite(context: Context, id: Long, user: AuthUserRecord): Intent
                = createIntent(context, ACTION_FAVORITE, id, user)

        @JvmStatic fun createFavorite(context: Context, status: Status, user: AuthUserRecord): Intent =
                Intent(context, AsyncCommandService::class.java).apply {
                    action = ACTION_FAVORITE
                    putExtra(EXTRA_TARGET_STATUS, status)
                    putExtra(EXTRA_USER, user)
                }

        @JvmStatic fun destroyFavorite(context: Context, id: Long, user: AuthUserRecord): Intent
                = createIntent(context, ACTION_UNFAVORITE, id, user)

        @JvmStatic fun destroyFavorite(context: Context, status: Status, user: AuthUserRecord): Intent =
                Intent(context, AsyncCommandService::class.java).apply {
                    action = ACTION_UNFAVORITE
                    putExtra(EXTRA_TARGET_STATUS, status)
                    putExtra(EXTRA_USER, user)
                }

        @JvmStatic fun createRetweet(context: Context, id: Long, user: AuthUserRecord): Intent
                = createIntent(context, ACTION_RETWEET, id, user)

        @JvmStatic fun createRepost(context: Context, status: Status, user: AuthUserRecord): Intent =
                Intent(context, AsyncCommandService::class.java).apply {
                    action = ACTION_RETWEET
                    putExtra(EXTRA_TARGET_STATUS, status)
                    putExtra(EXTRA_USER, user)
                }

        @JvmStatic fun createFavRT(context: Context, id: Long, user: AuthUserRecord): Intent
                = createIntent(context, ACTION_FAVRT, id, user)
    }

    //<editor-fold desc="Service Binder">
    override fun onCreate() {
        super.onCreate()
        putDebugLog("onCreate AsyncCommandService")
        bindService(Intent(this, TwitterService::class.java), connection, Context.BIND_AUTO_CREATE)
    }

    override fun onDestroy() {
        super.onDestroy()
        putDebugLog("onDestroy AsyncCommandService")
        unbindService(connection)
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            putDebugLog("onServiceConnected")
            service = (binder as TwitterService.TweetReceiverBinder).service
            serviceBound = true
        }

        override fun onServiceDisconnected(name: ComponentName) {
        }
    }
    //</editor-fold>
}
