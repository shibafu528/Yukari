package shibafu.yukari.filter.source

import shibafu.yukari.filter.sexp.ValueNode
import shibafu.yukari.twitter.AuthUserRecord
import shibafu.yukari.twitter.TwitterRestQuery
import shibafu.yukari.twitter.TwitterUtil
import twitter4j.RateLimitStatus
import twitter4j.ResponseList
import twitter4j.Status
import java.util.*

/**
 * In-Reply-Toで繋がっている会話を取得するためのフィルタソースです。
 */
class Trace(override val sourceAccount: AuthUserRecord?, val origin: String) : FilterSource {
    private val originId: Long = origin.toLongOrNull() ?: TwitterUtil.getStatusIdFromUrl(origin)

    override fun getRestQuery() = TwitterRestQuery { twitter, paging ->
        if (paging.maxId > -1) {
            // ページングには応答しない
            TraceResponseList()
        } else {
            val responses = arrayListOf<Status>()
            var accessLevel = 0
            var rateLimitStatus: RateLimitStatus? = null

            // 辿れる限り遡って取得する
            var searchId = originId
            while (searchId > -1) {
                val status = twitter.showStatus(searchId)
                responses += status
                accessLevel = status.accessLevel
                rateLimitStatus = status.rateLimitStatus

                searchId = status.inReplyToStatusId
            }

            TraceResponseList(responses, accessLevel, rateLimitStatus)
        }
    }

    override fun getStreamFilter() = ValueNode(false)

    private class TraceResponseList(items: Collection<Status> = emptyList(),
                                    private val _accessLevel: Int = 0,
                                    private val _rateLimitStatus: RateLimitStatus? = null) : ArrayList<Status>(), ResponseList<Status> {
        init {
            addAll(items)
        }

        override fun getAccessLevel() = _accessLevel

        override fun getRateLimitStatus() = _rateLimitStatus
    }
}