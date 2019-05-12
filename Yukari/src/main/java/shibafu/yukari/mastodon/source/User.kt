package shibafu.yukari.mastodon.source

import com.sys1yagi.mastodon4j.MastodonClient
import com.sys1yagi.mastodon4j.api.Pageable
import com.sys1yagi.mastodon4j.api.method.Accounts
import info.shibafu528.yukari.processor.filter.Source
import org.threeten.bp.ZonedDateTime
import shibafu.yukari.database.Provider
import shibafu.yukari.filter.sexp.AndNode
import shibafu.yukari.filter.sexp.ContainsNode
import shibafu.yukari.filter.sexp.NotNode
import shibafu.yukari.filter.sexp.SNode
import shibafu.yukari.filter.sexp.ValueNode
import shibafu.yukari.filter.sexp.VariableNode
import shibafu.yukari.filter.source.FilterSource
import shibafu.yukari.linkage.RestQuery
import shibafu.yukari.linkage.RestQueryException
import shibafu.yukari.mastodon.MastodonRestQuery
import shibafu.yukari.twitter.AuthUserRecord

/**
 * User Timeline
 */
@Source(apiType = Provider.API_MASTODON, slug = "user")
open class User(override val sourceAccount: AuthUserRecord, val target: String) : FilterSource {
    protected var targetId: Long? = if (target.startsWith("#")) target.substring(1).toLong() else null
    protected var missingAccount: Boolean = false

    override fun getRestQuery(): RestQuery? = MastodonRestQuery { client, range ->
        if (missingAccount) {
            throw RestQueryException(UserNotFoundException(target))
        }

        val targetId = targetId ?: findTargetId(client)

        Accounts(client).getStatuses(targetId, range = range).execute()
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

    protected fun findTargetId(client: MastodonClient): Long {
        val accountSearch = Accounts(client).getAccountSearch(target, 1).execute()

        if (accountSearch.isNotEmpty()) {
            return accountSearch.first().id.also { this.targetId = it }
        } else {
            missingAccount = true
            throw RestQueryException(UserNotFoundException(target))
        }
    }

    class UserNotFoundException(acct: String) : Exception("$acct を見つけることができませんでした。")
}

@Source(apiType = Provider.API_MASTODON, slug = "don_user_pinned")
class DonUserPinned(sourceAccount: AuthUserRecord, target: String) : User(sourceAccount, target) {
    override fun getRestQuery(): RestQuery? = MastodonRestQuery { client, range ->
        if (missingAccount) {
            throw RestQueryException(UserNotFoundException(target))
        }

        val targetId = targetId ?: findTargetId(client)

        val pageable = Accounts(client).getStatuses(targetId, pinned = true, range = range).execute()

        Pageable(pageable.part.sortedByDescending { ZonedDateTime.parse(it.createdAt) }, pageable.link)
    }
}
