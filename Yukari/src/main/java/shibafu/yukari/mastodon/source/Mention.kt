package shibafu.yukari.mastodon.source

import com.sys1yagi.mastodon4j.api.Pageable
import com.sys1yagi.mastodon4j.api.entity.Notification
import com.sys1yagi.mastodon4j.api.method.Notifications
import info.shibafu528.yukari.processor.filter.Source
import shibafu.yukari.database.Provider
import shibafu.yukari.filter.sexp.AndNode
import shibafu.yukari.filter.sexp.ContainsNode
import shibafu.yukari.filter.sexp.NotNode
import shibafu.yukari.filter.sexp.SNode
import shibafu.yukari.filter.sexp.ValueNode
import shibafu.yukari.filter.sexp.VariableNode
import shibafu.yukari.filter.source.FilterSource
import shibafu.yukari.linkage.RestQuery
import shibafu.yukari.mastodon.MastodonRestQuery
import shibafu.yukari.database.AuthUserRecord

/**
 * Mention Timeline
 */
@Source(apiType = Provider.API_MASTODON, slug = "mention")
data class Mention(override val sourceAccount: AuthUserRecord) : FilterSource {
    override fun getRestQuery(): RestQuery? = MastodonRestQuery { client, range ->
        val excludeTypes = listOf(Notification.Type.Follow, Notification.Type.Favourite, Notification.Type.Reblog)
        Notifications(client).getNotifications(range, excludeTypes).execute().let {
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