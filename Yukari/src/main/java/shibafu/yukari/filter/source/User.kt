package shibafu.yukari.filter.source

import info.shibafu528.yukari.processor.filter.Source
import shibafu.yukari.database.Provider
import shibafu.yukari.filter.sexp.EqualsNode
import shibafu.yukari.filter.sexp.SNode
import shibafu.yukari.filter.sexp.ValueNode
import shibafu.yukari.filter.sexp.VariableNode
import shibafu.yukari.database.AuthUserRecord
import shibafu.yukari.twitter.TwitterRestQuery

/**
 * 指定されたアカウントのUserタイムラインおよびUserStreamを対象とする抽出ソースです。
 *
 * Created by shibafu on 15/07/26.
 */
@Source(apiType = Provider.API_TWITTER, slug = "user")
public data class User(override val sourceAccount: AuthUserRecord?, val target: String) : FilterSource {
    private val targetId: Long? = if (target.startsWith("#")) target.substring(1).toLong() else null

    override fun getRestQuery() = TwitterRestQuery { twitter, paging ->
        if (targetId == null) {
            twitter.getUserTimeline(target, paging)
        } else {
            twitter.getUserTimeline(targetId, paging)
        }
    }

    override fun getStreamFilter(): SNode = EqualsNode(
            VariableNode(if (targetId == null) "user.screenName" else "user.id"),
            ValueNode(targetId ?: target)
    )
}