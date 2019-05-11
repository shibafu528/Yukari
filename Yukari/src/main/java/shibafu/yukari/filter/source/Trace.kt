package shibafu.yukari.filter.source

import info.shibafu528.yukari.processor.filter.Source
import shibafu.yukari.database.Provider
import shibafu.yukari.filter.sexp.ValueNode
import shibafu.yukari.linkage.RestQuery
import shibafu.yukari.linkage.RestQueryException
import shibafu.yukari.twitter.AuthUserRecord
import shibafu.yukari.twitter.TwitterUtil
import shibafu.yukari.twitter.entity.TwitterStatus
import twitter4j.Status
import twitter4j.Twitter
import twitter4j.TwitterException
import shibafu.yukari.entity.Status as IStatus

/**
 * In-Reply-Toで繋がっている会話を取得するためのフィルタソースです。
 */
@Source(apiType = Provider.API_TWITTER, slug = "trace")
data class Trace(override val sourceAccount: AuthUserRecord?, val origin: String) : FilterSource {
    private val originId: Long = origin.toLongOrNull() ?: TwitterUtil.getStatusIdFromUrl(origin)

    override fun getRestQuery() = object : RestQuery {
        override fun getRestResponses(userRecord: AuthUserRecord, api: Any, params: RestQuery.Params): kotlin.collections.List<IStatus> {
            api as Twitter

            // ページングには応答しない
            if (params.maxId > -1) {
                return emptyList()
            }

            try {
                val responses = arrayListOf<Status>()

                // 辿れる限り遡って取得する
                var searchId = originId
                while (searchId > -1) {
                    val status = api.showStatus(searchId)
                    responses += status

                    searchId = status.inReplyToStatusId
                }

                return responses.map { TwitterStatus(it, userRecord) }
            } catch (e: TwitterException) {
                throw RestQueryException(e)
            }
        }
    }

    override fun getStreamFilter() = ValueNode(false)
}