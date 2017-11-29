package shibafu.yukari.mastodon

import com.sys1yagi.mastodon4j.MastodonClient
import com.sys1yagi.mastodon4j.api.Range
import com.sys1yagi.mastodon4j.api.exception.Mastodon4jRequestException
import shibafu.yukari.entity.Status
import shibafu.yukari.linkage.RestQuery
import shibafu.yukari.linkage.RestQueryException

/**
 * RestQueryのMastodon用テンプレート
 */
class MastodonRestQuery(private val resolver: (MastodonClient, Range) -> List<Status>) : RestQuery {
    override fun getRestResponses(api: Any, maxId: Long, limitCount: Int, appendLoadMarker: Boolean, loadMarkerTag: String): List<Status> {
        api as MastodonClient
        val range = Range(maxId = maxId, limit = limitCount)

        try {
            val response = resolver(api, range)

            // TODO: ページングについてどうするか

            return response
        } catch (e: Mastodon4jRequestException) {
            throw RestQueryException(e)
        }
    }
}