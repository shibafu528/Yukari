package shibafu.yukari.filter.source

import shibafu.yukari.filter.sexp.ContainsNode
import shibafu.yukari.filter.sexp.SNode
import shibafu.yukari.filter.sexp.ValueNode
import shibafu.yukari.filter.sexp.VariableNode
import shibafu.yukari.twitter.AuthUserRecord
import shibafu.yukari.twitter.TwitterRestQuery

/**
 * 指定されたアカウントのHomeタイムラインおよびUserStreamを対象とする抽出ソースです。
 *
 * Created by shibafu on 15/07/26.
 */
data class Home(override val sourceAccount: AuthUserRecord) : FilterSource {

    override fun getRestQuery() = TwitterRestQuery({ twitter, paging -> twitter.getHomeTimeline(paging) })

    override fun getStreamFilter(): SNode = ContainsNode(
            VariableNode("receivedUsers"),
            ValueNode(sourceAccount)
    )
}