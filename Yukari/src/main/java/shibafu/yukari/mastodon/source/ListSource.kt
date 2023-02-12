package shibafu.yukari.mastodon.source

import com.sys1yagi.mastodon4j.api.method.Timelines
import info.shibafu528.yukari.processor.filter.Source
import shibafu.yukari.database.Provider
import shibafu.yukari.filter.sexp.SNode
import shibafu.yukari.filter.sexp.ValueNode
import shibafu.yukari.database.AuthUserRecord
import shibafu.yukari.filter.source.FilterSource
import shibafu.yukari.linkage.RestQueryException
import shibafu.yukari.mastodon.MastodonRestQuery

/**
 * 指定されたアカウントのListタイムラインを対象とする抽出ソースです。
 *
 * Created by yufushiro on 23/02/13.
 */
@Source(apiType = Provider.API_MASTODON, slug = "list")
data class ListSource(override val sourceAccount: AuthUserRecord, val target: String) : FilterSource {
    private val listId: Long by lazy {
        val (_, idStr) = target.split("/")
        idStr.toLongOrNull() ?: throw RestQueryException(sourceAccount, InvalidListIdException(target))
    }

    override fun getRestQuery() = MastodonRestQuery { client, range ->
        Timelines(client).getList(this.listId, range = range).execute()
    }

    override fun getStreamFilter(): SNode = ValueNode(false)

    class InvalidListIdException(target: String) : Exception("リストIDは数値で指定してください: $target")
}
