package shibafu.yukari.mastodon

import com.sys1yagi.mastodon4j.MastodonClient
import com.sys1yagi.mastodon4j.api.Range
import com.sys1yagi.mastodon4j.api.exception.Mastodon4jRequestException
import shibafu.yukari.entity.Status
import shibafu.yukari.linkage.RestQuery
import shibafu.yukari.linkage.RestQueryException
import shibafu.yukari.twitter.AuthUserRecord

/**
 * RestQueryのMastodon用テンプレート
 */
class MastodonRestQuery(private val resolver: (MastodonClient, Range) -> List<Status>) : RestQuery {
    override fun getRestResponses(userRecord: AuthUserRecord, api: Any, params: RestQuery.Params): List<Status> {
        api as MastodonClient
        val range = Range(maxId = if (params.maxId > -1) params.maxId else null, limit = params.limitCount)

        try {
            val response = resolver(api, range)

            // TODO: ページングについてどうするか

            return response
        } catch (e: Mastodon4jRequestException) {
            throw RestQueryException(e)
        }
    }
}