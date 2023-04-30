package shibafu.yukari.mastodon.streaming

import android.util.Log
import info.shibafu528.yukari.api.mastodon.ws.StreamClient
import okhttp3.OkHttpClient
import shibafu.yukari.database.AuthUserRecord

class StreamClientManager(private val okHttpBuilder: OkHttpClient.Builder, private val enforceLegacy: Boolean) {
    data class Ref(val client: StreamClient, internal val userRecord: AuthUserRecord)
    data class References(val client: StreamClient,
                          val refs: MutableList<Ref> = arrayListOf())

    private val references = hashMapOf<AuthUserRecord, References>()

    @Synchronized fun take(userRecord: AuthUserRecord): Ref {
        val refs = references[userRecord] ?: run {
            Log.d(StreamClientManager::class.java.simpleName, "take: connect ${userRecord.ScreenName}")

            val client = StreamClient.Builder("wss://${userRecord.Provider.host}", okHttpBuilder)
                .accessToken(userRecord.AccessToken)
                .enforceLegacy(enforceLegacy)
                .build()
            References(client).also { references[userRecord] = it }
        }

        return Ref(refs.client, userRecord).also {
            refs.refs.add(it)
            Log.d(StreamClientManager::class.java.simpleName, "take: (${userRecord.ScreenName}).refs = ${refs.refs.size}")
        }
    }

    @Synchronized fun release(ref: Ref) {
        val refs = references[ref.userRecord] ?: return
        refs.refs.remove(ref)
        Log.d(StreamClientManager::class.java.simpleName, "release: (${ref.userRecord.ScreenName}).refs = ${refs.refs.size}")

        if (refs.refs.isEmpty()) {
            Log.d(StreamClientManager::class.java.simpleName, "release: disconnect ${ref.userRecord.ScreenName}")
            refs.client.disconnect()
            references.remove(ref.userRecord)
        }
    }
}