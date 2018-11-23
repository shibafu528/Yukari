package shibafu.yukari.mastodon.source

import com.sys1yagi.mastodon4j.MastodonClient
import com.sys1yagi.mastodon4j.api.exception.Mastodon4jRequestException
import com.sys1yagi.mastodon4j.api.method.Statuses
import shibafu.yukari.entity.Status
import shibafu.yukari.filter.sexp.ValueNode
import shibafu.yukari.filter.source.FilterSource
import shibafu.yukari.linkage.RestQuery
import shibafu.yukari.linkage.RestQueryException
import shibafu.yukari.mastodon.entity.DonStatus
import shibafu.yukari.twitter.AuthUserRecord

/**
 * In-Reply-Toで繋がっている会話を取得するためのフィルタソースです。
 */
data class Trace(override val sourceAccount: AuthUserRecord?, val origin: String) : FilterSource {
    override fun getRestQuery() = object : RestQuery {
        override fun getRestResponses(userRecord: AuthUserRecord, api: Any, params: RestQuery.Params): List<Status> {
            api as MastodonClient

            // ページングには応答しない
            if (params.maxId > -1) {
                return emptyList()
            }

            try {
                val statuses = Statuses(api)
                val originId = origin.split("/").lastOrNull()?.toLongOrNull() ?: throw RestQueryException(RuntimeException("起点の指定フォーマットが不正です: $origin"))

                val originStatus = statuses.getStatus(originId).execute()
                val contexts = statuses.getContext(originId).execute()

                return (contexts.descendants.reversed() + listOf(originStatus) + contexts.ancestors.reversed())
                        .map { DonStatus(it, userRecord) }
            } catch (e: Mastodon4jRequestException) {
                throw RestQueryException(e)
            }
        }
    }

    override fun getStreamFilter() = ValueNode(false)
}