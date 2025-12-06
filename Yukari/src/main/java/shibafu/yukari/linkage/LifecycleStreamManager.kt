package shibafu.yukari.linkage

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.collection.ArrayMap
import androidx.collection.LongSparseArray
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import shibafu.yukari.database.AuthUserRecord
import shibafu.yukari.mastodon.MastodonStream
import shibafu.yukari.twitter.TwitterStream

/**
 * Androidのライフサイクルに合わせて [ProviderStream] の接続を管理するためのクラス。
 * アプリがバックグラウンドに回った時に自動で切断し、フォアグラウンドに復帰した時に再接続する。
 */
class LifecycleStreamManager(private val context: Context) : StreamCollectionProvider, DefaultLifecycleObserver {
    private val mutex = Any()
    private val providerStreams = arrayOf(TwitterStream(), MastodonStream())
    private var inForeground = false
    private var isStarted = false

    private val connectivityFlags = LongSparseArray<ArrayMap<String, Boolean>>()
    private val streamConnectivityListener = object : BroadcastReceiver() {
        private val handler = Handler(Looper.getMainLooper())

        override fun onReceive(context: Context, intent: Intent) {
            if (!PreferenceManager.getDefaultSharedPreferences(context).getBoolean("pref_notif_connectivity", true)) {
                return
            }

            when (val intent = SteamConnectivityIntents.fromIntent(intent)) {
                is SteamConnectivityIntents.Connected -> {
                    connectivityFlags[intent.user.InternalId]?.let { flags ->
                        val flag = flags[intent.channelId]
                        if (flag != null && !flag) {
                           showToast("${intent.channelName} Connected @${intent.user.ScreenName}")
                            flags[intent.channelId] = true
                        }
                    }
                }
                is SteamConnectivityIntents.Disconnected -> {
                    showToast("${intent.channelName} Disconnected @${intent.user.ScreenName}")

                    var flags = connectivityFlags[intent.user.InternalId] ?: ArrayMap<String, Boolean>().also { connectivityFlags.put(intent.user.InternalId, it) }
                    flags[intent.channelId] = false
                }
                null -> {}
            }
        }

        private fun showToast(text: String) {
            handler.post {
                Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
            }
        }
    }

    init {
        LocalBroadcastManager.getInstance(context).let { bm ->
            bm.registerReceiver(streamConnectivityListener, IntentFilter(SteamConnectivityIntents.ACTION_STREAM_CONNECTED))
            bm.registerReceiver(streamConnectivityListener, IntentFilter(SteamConnectivityIntents.ACTION_STREAM_DISCONNECTED))
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        synchronized(mutex) {
            Log.d(LOG_TAG, "onStart (isStarted: $isStarted)")

            inForeground = true
            providerStreams.forEach { stream -> stream.onCreate(context) }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        synchronized(mutex) {
            Log.d(LOG_TAG, "onStop")

            inForeground = false
            providerStreams.forEach { stream -> stream.onDestroy() }
        }
    }

    override fun getProviderStream(userRecord: AuthUserRecord): ProviderStream? {
        val apiType = userRecord.Provider.apiType
        if (apiType in 0..providerStreams.size) {
            return providerStreams[apiType]
        }
        return null
    }

    override fun getProviderStream(apiType: Int): ProviderStream {
        if (apiType in 0..providerStreams.size) {
            return providerStreams[apiType]
        }
        throw UnsupportedOperationException("API Type $apiType not implemented.")
    }

    override fun getProviderStreams(): Array<out ProviderStream?> {
        return providerStreams
    }

    override fun startStreamChannels() {
        synchronized(mutex) {
            Log.d(LOG_TAG, "startStreamChannels")

            isStarted = true
            StreamCollectionProvider.startStreamChannels(this)
        }
    }

    fun onAddUser(userRecord: AuthUserRecord) {
        synchronized(mutex) {
            Log.d(LOG_TAG, "onAddUser (isStarted: $isStarted)")

            // バックグラウンド時は、次回のonStart()で処理されるので何もしなくて良い
            if (!inForeground) {
                return
            }

            getProviderStream(userRecord)?.addUser(userRecord)
            if (isStarted) {
                startStreamChannels()
            }
        }
    }

    fun onRemoveUser(userRecord: AuthUserRecord) {
        synchronized(mutex) {
            Log.d(LOG_TAG, "onRemoveUser")

            // バックグラウンド時は、次回のonStart()で処理されるので何もしなくて良い
            if (!inForeground) {
                return
            }

            getProviderStream(userRecord)?.removeUser(userRecord)
        }
    }

    companion object {
        private const val LOG_TAG = "LifecycleStreamManager"
    }
}