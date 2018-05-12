package shibafu.yukari.mastodon.source

import com.google.gson.Gson
import com.sys1yagi.mastodon4j.MastodonClient
import com.sys1yagi.mastodon4j.api.Range
import com.sys1yagi.mastodon4j.api.exception.Mastodon4jRequestException
import com.sys1yagi.mastodon4j.api.method.Public
import okhttp3.OkHttpClient
import shibafu.yukari.entity.Status
import shibafu.yukari.filter.sexp.AndNode
import shibafu.yukari.filter.sexp.ContainsNode
import shibafu.yukari.filter.sexp.SNode
import shibafu.yukari.filter.sexp.ValueNode
import shibafu.yukari.filter.sexp.VariableNode
import shibafu.yukari.filter.source.FilterSource
import shibafu.yukari.linkage.RestQuery
import shibafu.yukari.linkage.RestQueryException
import shibafu.yukari.mastodon.MastodonRestQuery
import shibafu.yukari.mastodon.entity.DonStatus
import shibafu.yukari.mastodon.sexp.UrlHostEqualPredicate
import shibafu.yukari.twitter.AuthUserRecord

/**
 * Local Public Timeline
 */
class Public(override val sourceAccount: AuthUserRecord) : FilterSource {
    override fun getRestQuery(): RestQuery = MastodonRestQuery { client, range ->
        Public(client).getLocalPublic(range).execute()
    }

    override fun getStreamFilter(): SNode = AndNode(
            ContainsNode(
                    VariableNode("receivedUsers"),
                    ValueNode(sourceAccount)
            ),
            UrlHostEqualPredicate(
                    VariableNode("url"),
                    ValueNode(sourceAccount.Provider.host)
            )
    )
}

/**
 * Local Public Timeline (Anonymous access)
 */
class AnonymousPublic(override val sourceAccount: AuthUserRecord, val instance: String) : FilterSource {
    // び、微妙～～
    override fun getRestQuery(): RestQuery = object : RestQuery {
        override fun getRestResponses(userRecord: AuthUserRecord, api: Any, params: RestQuery.Params): List<Status> {
            val client = MastodonClient.Builder(instance, OkHttpClient.Builder(), Gson()).build()
            val request = Public(client).getLocalPublic(Range(maxId = if (params.maxId > -1) params.maxId else null, limit = params.limitCount))
            try {
                val response = request.execute()
                return response.part.map { DonStatus(it, sourceAccount) }
            } catch (e: Mastodon4jRequestException) {
                throw RestQueryException(e)
            }
        }
    }

    override fun getStreamFilter(): SNode = UrlHostEqualPredicate(
            VariableNode("url"),
            ValueNode(instance)
    )
}