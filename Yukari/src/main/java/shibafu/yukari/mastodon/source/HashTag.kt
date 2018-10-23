package shibafu.yukari.mastodon.source

import com.sys1yagi.mastodon4j.api.method.Public
import shibafu.yukari.filter.sexp.AndNode
import shibafu.yukari.filter.sexp.ContainsNode
import shibafu.yukari.filter.sexp.SNode
import shibafu.yukari.filter.sexp.ValueNode
import shibafu.yukari.filter.sexp.VariableNode
import shibafu.yukari.filter.source.FilterSource
import shibafu.yukari.mastodon.MastodonRestQuery
import shibafu.yukari.twitter.AuthUserRecord

/**
 * HashTag Search
 */
class HashTag(override val sourceAccount: AuthUserRecord, val tag: String) : FilterSource {
    private val normalizedTag: String = tag.trim().trimStart('#')

    override fun getRestQuery() = MastodonRestQuery { client, range ->
        Public(client).getFederatedTag(normalizedTag, range).execute()
    }

    override fun getStreamFilter(): SNode = AndNode(
            ContainsNode(
                    VariableNode("receivedUsers"),
                    ValueNode(sourceAccount)
            ),
            ContainsNode(
                    VariableNode("text"),
                    ValueNode("#$normalizedTag")
            )
    )
}

/**
 * Local HashTag Search
 */
class LocalHashTag(override val sourceAccount: AuthUserRecord, val tag: String) : FilterSource {
    private val normalizedTag: String = tag.trim().trimStart('#')

    override fun getRestQuery() = MastodonRestQuery { client, range ->
        Public(client).getLocalTag(normalizedTag, range).execute()
    }

    override fun getStreamFilter(): SNode = AndNode(
            ContainsNode(
                    VariableNode("receivedUsers"),
                    ValueNode(sourceAccount)
            ),
            ContainsNode(
                    VariableNode("text"),
                    ValueNode("#$normalizedTag")
            )
    )
}