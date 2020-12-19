package shibafu.yukari.filter.source

import info.shibafu528.yukari.processor.filter.Source
import shibafu.yukari.database.Provider
import shibafu.yukari.filter.sexp.SNode
import shibafu.yukari.filter.sexp.ValueNode
import shibafu.yukari.database.AuthUserRecord
import shibafu.yukari.twitter.TwitterRestQuery

/**
 * 指定されたアカウントのお気に入り一覧を対象とする抽出ソースです。
 */
@Source(apiType = Provider.API_TWITTER, slug = "favorite")
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