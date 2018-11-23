package shibafu.yukari.mastodon.source

import com.sys1yagi.mastodon4j.api.method.Timelines
import shibafu.yukari.database.Provider
import shibafu.yukari.filter.sexp.AndNode
import shibafu.yukari.filter.sexp.ContainsNode
import shibafu.yukari.filter.sexp.EqualsNode
import shibafu.yukari.filter.sexp.SNode
import shibafu.yukari.filter.sexp.ValueNode
import shibafu.yukari.filter.sexp.VariableNode
import shibafu.yukari.filter.source.FilterSource
import shibafu.yukari.linkage.RestQuery
import shibafu.yukari.mastodon.MastodonRestQuery
import shibafu.yukari.mastodon.MastodonStream
import shibafu.yukari.twitter.AuthUserRecord

/**
 * Home Timeline
 */
data class Home(override val sourceAccount: AuthUserRecord) : FilterSource {
    override fun getRestQuery(): RestQuery? = MastodonRestQuery { client, range ->
        Timelines(client).getHome(range).execute()
    }

    override fun getStreamFilter(): SNode = AndNode(
            EqualsNode(
                    VariableNode("providerApiType"),
                    ValueNode(Provider.API_MASTODON)
            ),
            ContainsNode(
                    VariableNode("receivedUsers"),
                    ValueNode(sourceAccount)
            ),
            EqualsNode(
                    VariableNode("\$timelineId"),
                    ValueNode(MastodonStream.USER_STREAM_ID)
            )
    )
}