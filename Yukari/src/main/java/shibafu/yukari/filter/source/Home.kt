package shibafu.yukari.filter.source

import android.content.Context
import shibafu.yukari.twitter.AuthUserRecord
import shibafu.yukari.twitter.PreformedResponseList
import shibafu.yukari.twitter.RESTLoader
import shibafu.yukari.twitter.rest.RESTParams
import shibafu.yukari.twitter.statusimpl.PreformedStatus
import shibafu.yukari.twitter.statusmanager.RestQuery
import shibafu.yukari.twitter.streaming.FilterStream
import twitter4j.Paging
import twitter4j.Twitter

/**
 * 指定されたアカウントのHomeタイムラインおよびUserStreamを対象とする抽出ソースです。
 *
 * Created by shibafu on 15/07/26.
 */
public class Home(override val sourceAccount: AuthUserRecord?) : FilterSource {

    override fun getRestQuery() = RestQuery { twitter, paging -> twitter.getHomeTimeline(paging) }

    override fun requireUserStream(): Boolean = true

    override fun getFilterStream(context: Context): FilterStream? = null
}