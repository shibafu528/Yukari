package shibafu.yukari.filter.source

import android.content.Context
import shibafu.yukari.filter.sexp.EqualsNode
import shibafu.yukari.filter.sexp.SNode
import shibafu.yukari.filter.sexp.ValueNode
import shibafu.yukari.filter.sexp.VariableNode
import shibafu.yukari.twitter.AuthUserRecord
import shibafu.yukari.twitter.statusmanager.RestQuery
import shibafu.yukari.twitter.streaming.FilterStream

/**
 * 指定されたアカウントのUserタイムラインおよびUserStreamを対象とする抽出ソースです。
 *
 * Created by shibafu on 15/07/26.
 */
public data class User(override val sourceAccount: AuthUserRecord?, val target: String) : FilterSource {
    private val targetId: Long? = if (target.startsWith("#")) target.substring(1).toLong() else null

    override fun getRestQuery() = RestQuery { twitter, paging ->
        if (targetId == null) {
            twitter.getUserTimeline(target, paging)
        } else {
            twitter.getUserTimeline(targetId, paging)
        }
    }

    override fun requireUserStream(): Boolean = false

    override fun getFilterStream(context: Context): FilterStream? = null

    override fun filterUserStream(): SNode = EqualsNode(
            VariableNode(if (targetId == null) "user.screenName" else "user.id"),
            ValueNode(targetId ?: target)
    )
}