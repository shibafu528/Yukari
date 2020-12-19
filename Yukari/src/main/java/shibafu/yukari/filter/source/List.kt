package shibafu.yukari.filter.source

import info.shibafu528.yukari.processor.filter.Source
import shibafu.yukari.database.Provider
import shibafu.yukari.filter.sexp.SNode
import shibafu.yukari.filter.sexp.ValueNode
import shibafu.yukari.database.AuthUserRecord
import shibafu.yukari.twitter.TwitterRestQuery

/**
 * 指定されたアカウントのListタイムラインを対象とする抽出ソースです。
 *
 * Created by shibafu on 16/08/05.
 */
@Source(apiType = Provider.API_TWITTER, slug = "list")
public data class List(override val sourceAccount: AuthUserRecord?, val target: String) : FilterSource {
    private val ownerScreenName: String
    private val slug: String
    private val id: Long

    init {
        val (ownerScreenName, slug) = target.split("/")
        this.ownerScreenName = ownerScreenName
        when (val id = slug.toLongOrNull()) {
            is Long -> {
                this.id = id
                this.slug = ""
            }
            else -> {
                this.id = -1
                this.slug = slug
            }
        }
    }

    override fun getRestQuery() = TwitterRestQuery { twitter, paging ->
        if (id > -1) {
            twitter.getUserListStatuses(id, paging)
        } else {
            twitter.getUserListStatuses(ownerScreenName, slug, paging)
        }
    }

    override fun getStreamFilter(): SNode = ValueNode(false)
}