package shibafu.yukari.filter.source

import shibafu.yukari.filter.sexp.SNode
import shibafu.yukari.filter.sexp.ValueNode
import shibafu.yukari.twitter.AuthUserRecord
import shibafu.yukari.twitter.TwitterRestQuery

/**
 * 指定されたアカウントのお気に入り一覧を対象とする抽出ソースです。
 */
public data class Favorite(override val sourceAccount: AuthUserRecord?, val target: String) : FilterSource {
    private val targetId: Long? = if (target.startsWith("#")) target.substring(1).toLong() else null

    override fun getRestQuery() = TwitterRestQuery { twitter, paging ->
        if (targetId == null) {
            twitter.getFavorites(target, paging)
        } else {
            twitter.getFavorites(targetId, paging)
        }
    }

    override fun getStreamFilter(): SNode = ValueNode(false)
}