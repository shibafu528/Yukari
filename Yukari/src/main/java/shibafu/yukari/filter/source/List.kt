package shibafu.yukari.filter.source

import android.content.Context
import shibafu.yukari.filter.sexp.AndNode
import shibafu.yukari.filter.sexp.EqualsNode
import shibafu.yukari.filter.sexp.SNode
import shibafu.yukari.filter.sexp.ValueNode
import shibafu.yukari.filter.sexp.VariableNode
import shibafu.yukari.twitter.AuthUserRecord
import shibafu.yukari.twitter.statusimpl.MetaStatus
import shibafu.yukari.twitter.statusmanager.RestQuery
import shibafu.yukari.twitter.streaming.FilterStream
import shibafu.yukari.twitter.streaming.RestStream

/**
 * 指定されたアカウントのListタイムラインを対象とする抽出ソースです。
 *
 * Created by shibafu on 16/08/05.
 */
public data class List(override val sourceAccount: AuthUserRecord?, val target: String) : FilterSource {
    private val ownerScreenName: String
    private val slug: String

    init {
        val (ownerScreenName, slug) = target.split("/")
        this.ownerScreenName = ownerScreenName
        this.slug = slug
    }

    override fun getRestQuery() = RestQuery { twitter, paging ->
        twitter.getUserListStatuses(ownerScreenName, slug, paging)
    }

    override fun requireUserStream(): Boolean = false

    override fun getFilterStream(context: Context): FilterStream? = null

    override fun filterUserStream(): SNode = AndNode(
            // TODO: これだとRESTリクエストによる結果がなんでも通ってしまうので他のソースにも影響が出る
            EqualsNode(
                    VariableNode("class.simpleName"),
                    ValueNode(MetaStatus::class.java.simpleName)
            ),
            EqualsNode(
                    VariableNode("metadata"),
                    ValueNode(RestStream::class.java.simpleName)
            )
    )
}