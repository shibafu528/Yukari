package shibafu.yukari.mastodon.source

import com.sys1yagi.mastodon4j.api.method.Timelines
import info.shibafu528.yukari.processor.filter.Source
import shibafu.yukari.database.AuthUserRecord
import shibafu.yukari.database.Provider
import shibafu.yukari.filter.sexp.AndNode
import shibafu.yukari.filter.sexp.ContainsNode
import shibafu.yukari.filter.sexp.EqualsNode
import shibafu.yukari.filter.sexp.NotEqualsNode
import shibafu.yukari.filter.sexp.SNode
import shibafu.yukari.filter.sexp.ValueNode
import shibafu.yukari.filter.sexp.VariableNode
import shibafu.yukari.filter.source.FilterSource
import shibafu.yukari.linkage.RestQuery
import shibafu.yukari.mastodon.MastodonRestQuery
import shibafu.yukari.mastodon.MastodonStream
import shibafu.yukari.mastodon.entity.DonNotification

/**
 * Home Timeline
 */
@Source(apiType = Provider.API_MASTODON, slug = "home")
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
            ),
            NotEqualsNode(
                    VariableNode("class.name"),
                    ValueNode(DonNotification::class.qualifiedName)
            )
    )
}