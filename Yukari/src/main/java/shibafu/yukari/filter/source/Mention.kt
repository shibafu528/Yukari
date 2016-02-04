package shibafu.yukari.filter.source

import android.content.Context
import shibafu.yukari.filter.sexp.*
import shibafu.yukari.twitter.AuthUserRecord
import shibafu.yukari.twitter.statusmanager.RestQuery
import shibafu.yukari.twitter.streaming.FilterStream

/**
 * 指定されたアカウントのMentionsタイムラインおよびUserStreamを対象とする抽出ソースです。
 *
 * Created by shibafu on 15/07/26.
 */
public class Mention(override val sourceAccount: AuthUserRecord) : FilterSource {

    override fun getRestQuery() = RestQuery { twitter, paging -> twitter.getMentionsTimeline(paging) }

    override fun requireUserStream(): Boolean = true

    override fun getFilterStream(context: Context): FilterStream? = null

    override fun filterUserStream(): SNode = AndNode(
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