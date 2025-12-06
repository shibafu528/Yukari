package shibafu.yukari.linkage

import android.content.Context
import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import shibafu.yukari.database.AuthUserRecord

sealed class SteamConnectivityIntents(
    private val action: String,
    val user: AuthUserRecord,
    val channelId: String,
    val channelName: String
) {
    class Connected(user: AuthUserRecord, channelId: String, channelName: String) : SteamConnectivityIntents(ACTION_STREAM_CONNECTED, user, channelId, channelName)
    class Disconnected(user: AuthUserRecord, channelId: String, channelName: String) : SteamConnectivityIntents(ACTION_STREAM_DISCONNECTED, user, channelId, channelName)

    fun toIntent() = Intent(action).apply {
        putExtra(EXTRA_USER, user)
        putExtra(EXTRA_CHANNEL_ID, channelId)
        putExtra(EXTRA_CHANNEL_NAME, channelName)
    }

    fun sendBroadcast(context: Context) {
        LocalBroadcastManager.getInstance(context).sendBroadcast(toIntent())
    }

    companion object {
        const val ACTION_STREAM_CONNECTED: String = "shibafu.yukari.STREAM_CONNECTED"
        const val ACTION_STREAM_DISCONNECTED: String = "shibafu.yukari.STREAM_DISCONNECTED"
        private const val EXTRA_USER: String = "user"
        private const val EXTRA_CHANNEL_ID: String = "channelId"
        private const val EXTRA_CHANNEL_NAME: String = "channelName"

        fun fromIntent(intent: Intent): SteamConnectivityIntents? {
            return when (intent.action) {
                ACTION_STREAM_CONNECTED -> Connected(intent.getSerializableExtra(EXTRA_USER) as AuthUserRecord, intent.getStringExtra(EXTRA_CHANNEL_ID)!!, intent.getStringExtra(EXTRA_CHANNEL_NAME)!!)
                ACTION_STREAM_DISCONNECTED -> Disconnected(intent.getSerializableExtra(EXTRA_USER) as AuthUserRecord, intent.getStringExtra(EXTRA_CHANNEL_ID)!!, intent.getStringExtra(EXTRA_CHANNEL_NAME)!!)
                else -> null
            }
        }
    }
}