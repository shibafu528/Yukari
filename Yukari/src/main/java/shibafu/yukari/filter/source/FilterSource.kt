package shibafu.yukari.filter.source

import android.content.Context
import shibafu.yukari.twitter.AuthUserRecord
import shibafu.yukari.twitter.PreformedResponseList
import shibafu.yukari.twitter.RESTLoader
import shibafu.yukari.twitter.rest.RESTParams
import shibafu.yukari.twitter.statusimpl.PreformedStatus
import shibafu.yukari.twitter.streaming.Stream
import shibafu.yukari.twitter.streaming.StreamUser

/**
 * Created by shibafu on 15/06/07.
 */
public open data class FilterSource(val resource: FilterSource.Resource,
                                     val sourceAccount: AuthUserRecord?) {

    public open fun getRESTLoader(context: Context, iface: RESTLoader.RESTLoaderInterface?)
            : RESTLoader<RESTParams, PreformedResponseList<PreformedStatus>>? {
        return null
    }

    public open fun getStream(context: Context) : Stream? = sourceAccount?.let { StreamUser(context, it) }

    public enum class Resource {
        ALL,
        HOME,
        MENTION,
        LIST,
        SEARCH,
        TRACK,
        TRACE,
        USER,
        FAVORITE,
        BOOKMARK
    }
}

