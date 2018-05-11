package shibafu.yukari.filter.source

import shibafu.yukari.filter.sexp.SNode
import shibafu.yukari.filter.sexp.ValueNode
import shibafu.yukari.twitter.AuthUserRecord
import shibafu.yukari.twitter.TwitterRestQuery

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

    override fun getRestQuery() = TwitterRestQuery { twitter, paging ->
        twitter.getUserListStatuses(ownerScreenName, slug, paging)
    }

    override fun getStreamFilter(): SNode = ValueNode(false)
}