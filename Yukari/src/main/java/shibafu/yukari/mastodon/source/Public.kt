package shibafu.yukari.mastodon.source

import android.content.Context
import com.google.gson.Gson
import com.sys1yagi.mastodon4j.MastodonClient
import com.sys1yagi.mastodon4j.api.Range
import com.sys1yagi.mastodon4j.api.exception.Mastodon4jRequestException
import com.sys1yagi.mastodon4j.api.method.Public
import okhttp3.OkHttpClient
import shibafu.yukari.entity.Status
import shibafu.yukari.filter.source.FilterSource
import shibafu.yukari.linkage.RestQuery
import shibafu.yukari.linkage.RestQueryException
import shibafu.yukari.mastodon.entity.DonStatus
import shibafu.yukari.twitter.AuthUserRecord
import shibafu.yukari.twitter.streaming.FilterStream

/**
 * Local Public Timeline
 */
class Public(override val sourceAccount: AuthUserRecord, val instance: String) : FilterSource {

    // び、微妙～～
    override fun getRestQuery(): RestQuery = object : RestQuery {
        override fun getRestResponses(userRecord: AuthUserRecord, api: Any, maxId: Long, limitCount: Int, appendLoadMarker: Boolean, loadMarkerTag: String): List<Status> {
            val client = MastodonClient.Builder(instance, OkHttpClient.Builder(), Gson()).build()
            val request = Public(client).getLocalPublic(Range(maxId = if (maxId > -1) maxId else null, limit = limitCount))
            try {
                val response = request.execute()
                return response.part.map { DonStatus(it, sourceAccount) }
            } catch (e: Mastodon4jRequestException) {
                throw RestQueryException(e)
            }
        }
    }

    override fun requireUserStream(): Boolean = false

    override fun getFilterStream(context: Context): FilterStream? = null
}