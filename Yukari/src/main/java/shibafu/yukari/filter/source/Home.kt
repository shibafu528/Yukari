package shibafu.yukari.filter.source

import android.content.Context
import shibafu.yukari.twitter.AuthUserRecord
import shibafu.yukari.twitter.PreformedResponseList
import shibafu.yukari.twitter.RESTLoader
import shibafu.yukari.twitter.rest.RESTParams
import shibafu.yukari.twitter.statusimpl.PreformedStatus
import shibafu.yukari.twitter.streaming.FilterStream

/**
 * 指定されたアカウントのHomeタイムラインおよびUserStreamを対象とする抽出ソースです。
 *
 * Created by shibafu on 15/07/26.
 */
public class Home(override val sourceAccount: AuthUserRecord?) : FilterSource {

    override fun getRESTLoader(context: Context, iface: RESTLoader.RESTLoaderInterface?)
            : RESTLoader<RESTParams, PreformedResponseList<PreformedStatus>>? {
        throw UnsupportedOperationException()
    }

    override fun requireUserStream(): Boolean = true

    override fun getFilterStream(context: Context): FilterStream? = null
}