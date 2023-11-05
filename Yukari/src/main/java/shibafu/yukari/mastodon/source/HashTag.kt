package shibafu.yukari.mastodon.source

import android.content.Context
import com.sys1yagi.mastodon4j.api.method.Public
import info.shibafu528.yukari.processor.filter.Source
import shibafu.yukari.database.Provider
import shibafu.yukari.filter.sexp.AndNode
import shibafu.yukari.filter.sexp.ContainsNode
import shibafu.yukari.filter.sexp.SNode
import shibafu.yukari.filter.sexp.ValueNode
import shibafu.yukari.filter.sexp.VariableNode
import shibafu.yukari.filter.source.DynamicChannelController
import shibafu.yukari.filter.source.FilterSource
import shibafu.yukari.linkage.ProviderStream
import shibafu.yukari.mastodon.MastodonRestQuery
import shibafu.yukari.mastodon.MastodonStream
import shibafu.yukari.database.AuthUserRecord

/**
 * HashTag Search
 */
@Source(apiType = Provider.API_MASTODON, slug = "don_hashtag")
data class HashTag(override val sourceAccount: AuthUserRecord, val tag: String) : FilterSource {
    val normalizedTag: String = tag.trim().trimStart('#')

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

    override fun getDynamicChannelController() = object : DynamicChannelController {
        override fun isConnected(context: Context, stream: ProviderStream): Boolean {
            stream as MastodonStream
            return stream.isConnectedHashTagStream(sourceAccount, normalizedTag, MastodonStream.Scope.FEDERATED)
        }

        override fun connect(context: Context, stream: ProviderStream) {
            stream as MastodonStream
            stream.startHashTagStream(sourceAccount, normalizedTag, MastodonStream.Scope.FEDERATED)
        }

        override fun disconnect(context: Context, stream: ProviderStream) {
            stream as MastodonStream
            stream.stopHashTagStream(sourceAccount, normalizedTag, MastodonStream.Scope.FEDERATED)
        }
    }
}

/**
 * Local HashTag Search
 */
@Source(apiType = Provider.API_MASTODON, slug = "don_local_hashtag")
data class LocalHashTag(override val sourceAccount: AuthUserRecord, val tag: String) : FilterSource {
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

    override fun getDynamicChannelController() = object : DynamicChannelController {
        override fun isConnected(context: Context, stream: ProviderStream): Boolean {
            stream as MastodonStream
            return stream.isConnectedHashTagStream(sourceAccount, normalizedTag, MastodonStream.Scope.LOCAL)
        }

        override fun connect(context: Context, stream: ProviderStream) {
            stream as MastodonStream
            stream.startHashTagStream(sourceAccount, normalizedTag, MastodonStream.Scope.LOCAL)
        }

        override fun disconnect(context: Context, stream: ProviderStream) {
            stream as MastodonStream
            stream.stopHashTagStream(sourceAccount, normalizedTag, MastodonStream.Scope.LOCAL)
        }
    }
}