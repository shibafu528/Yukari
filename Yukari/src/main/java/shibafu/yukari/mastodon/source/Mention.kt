package shibafu.yukari.mastodon.source

import android.content.Context
import com.sys1yagi.mastodon4j.api.Pageable
import com.sys1yagi.mastodon4j.api.method.Notifications
import shibafu.yukari.filter.sexp.ContainsNode
import shibafu.yukari.filter.sexp.SNode
import shibafu.yukari.filter.sexp.ValueNode
import shibafu.yukari.filter.sexp.VariableNode
import shibafu.yukari.filter.source.FilterSource
import shibafu.yukari.linkage.RestQuery
import shibafu.yukari.mastodon.MastodonRestQuery
import shibafu.yukari.twitter.AuthUserRecord
import shibafu.yukari.twitter.streaming.FilterStream

/**
 * Mention Timeline
 */
class Mention(override val sourceAccount: AuthUserRecord) : FilterSource {
    override fun getRestQuery(): RestQuery? = MastodonRestQuery { client, range ->
        Notifications(client).getNotifications(range).execute().let {
            Pageable(it.part.filter { it.type == "mention" }.mapNotNull { it.status }, it.link)
        }
    }

    override fun requireUserStream(): Boolean = true

    override fun getFilterStream(context: Context): FilterStream? = null

    override fun filterUserStream(): SNode = ContainsNode(
            VariableNode("receivedUsers"),
            ValueNode(sourceAccount)
    )
}