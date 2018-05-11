package shibafu.yukari.filter.source

import shibafu.yukari.filter.sexp.AndNode
import shibafu.yukari.filter.sexp.ContainsNode
import shibafu.yukari.filter.sexp.NotNode
import shibafu.yukari.filter.sexp.SNode
import shibafu.yukari.filter.sexp.ValueNode
import shibafu.yukari.filter.sexp.VariableNode
import shibafu.yukari.twitter.AuthUserRecord
import shibafu.yukari.twitter.TwitterRestQuery

/**
 * 指定されたアカウントのMentionsタイムラインおよびUserStreamを対象とする抽出ソースです。
 *
 * Created by shibafu on 15/07/26.
 */
public data class Mention(override val sourceAccount: AuthUserRecord) : FilterSource {

    override fun getRestQuery() = TwitterRestQuery { twitter, paging -> twitter.getMentionsTimeline(paging) }

    override fun getStreamFilter(): SNode = AndNode(
            ContainsNode(
                VariableNode("receivedUsers"),
                ValueNode(sourceAccount)
            ),
            NotNode(
                    VariableNode("retweet")
            ),
            VariableNode("mentionedToMe")
    )
}