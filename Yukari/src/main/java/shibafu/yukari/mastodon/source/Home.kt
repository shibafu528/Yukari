package shibafu.yukari.mastodon.source

import android.content.Context
import com.sys1yagi.mastodon4j.api.method.Timelines
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
 * Home Timeline
 */
class Home(override val sourceAccount: AuthUserRecord) : FilterSource {
    override fun getRestQuery(): RestQuery? = MastodonRestQuery { client, range ->
        Timelines(client).getHome(range).execute()
    }

    override fun requireUserStream(): Boolean = true

    override fun getFilterStream(context: Context): FilterStream? = null

    override fun filterUserStream(): SNode = ContainsNode(
            VariableNode("receivedUsers"),
            ValueNode(sourceAccount)
    )
}