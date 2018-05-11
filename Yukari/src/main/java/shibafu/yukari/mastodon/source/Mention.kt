package shibafu.yukari.mastodon.source

import com.sys1yagi.mastodon4j.api.Pageable
import com.sys1yagi.mastodon4j.api.method.Notifications
import shibafu.yukari.filter.sexp.AndNode
import shibafu.yukari.filter.sexp.ContainsNode
import shibafu.yukari.filter.sexp.NotNode
import shibafu.yukari.filter.sexp.SNode
import shibafu.yukari.filter.sexp.ValueNode
import shibafu.yukari.filter.sexp.VariableNode
import shibafu.yukari.filter.source.FilterSource
import shibafu.yukari.linkage.RestQuery
import shibafu.yukari.mastodon.MastodonRestQuery
import shibafu.yukari.twitter.AuthUserRecord

/**
 * Mention Timeline
 */
class Mention(override val sourceAccount: AuthUserRecord) : FilterSource {
    override fun getRestQuery(): RestQuery? = MastodonRestQuery { client, range ->
        Notifications(client).getNotifications(range, listOf("follow", "favourite", "reblog")).execute().let {
            Pageable(it.part.filter { it.type == "mention" }.mapNotNull { it.status }, it.link)
        }
    }

    override fun getStreamFilter(): SNode = AndNode(
            ContainsNode(
                    VariableNode("receivedUsers"),
                    ValueNode(sourceAccount)
            ),
            NotNode(
                    VariableNode("repost")
            ),
            VariableNode("mentionedToMe")
    )
}