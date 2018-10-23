package shibafu.yukari.filter.source

import com.google.gson.Gson
import shibafu.yukari.database.Provider
import shibafu.yukari.entity.LoadMarker
import shibafu.yukari.entity.Status
import shibafu.yukari.filter.sexp.AndNode
import shibafu.yukari.filter.sexp.ContainsNode
import shibafu.yukari.filter.sexp.SNode
import shibafu.yukari.filter.sexp.ValueNode
import shibafu.yukari.filter.sexp.VariableNode
import shibafu.yukari.linkage.RestQuery
import shibafu.yukari.linkage.RestQueryException
import shibafu.yukari.twitter.AuthUserRecord
import shibafu.yukari.twitter.entity.TwitterStatus
import shibafu.yukari.twitter.streaming.FilterStream
import twitter4j.Query
import twitter4j.Twitter
import twitter4j.TwitterException
import java.util.*

/**
 * Twitterの検索APIを対象とする抽出ソースです。
 */
class Search(override val sourceAccount: AuthUserRecord?, val query: String) : FilterSource {
    private val parsedQuery = FilterStream.ParsedQuery(query)
    private val gson = Gson()

    override fun getRestQuery() = object : RestQuery {
        override fun getRestResponses(userRecord: AuthUserRecord, api: Any, params: RestQuery.Params): MutableList<Status> {
            api as Twitter
            val searchQuery = if (params.stringCursor.isEmpty()) {
                Query(query).apply {
                    count = params.limitCount
                    resultType = Query.RECENT
                }
            } else {
                gson.fromJson(params.stringCursor, Query::class.java)
            }

            try {
                val result = api.search(searchQuery)

                // Search APIはExtended Entitiesなどが欠落している時代があったため、ここでは安全をとってLookup APIで再取得する
                val ids = result.tweets.map { it.id }.toLongArray()
                val responseList: MutableList<Status> = api.lookup(*ids).map { TwitterStatus(it, userRecord) }.toMutableList()

                if (params.appendLoadMarker && result.hasNext() && responseList.isNotEmpty()) {
                    val last = responseList.last()
                    val stringQuery = gson.toJson(result.nextQuery())
                    responseList += LoadMarker(last.id - 1, Provider.API_TWITTER, last.id, userRecord, params.loadMarkerTag, Date(last.createdAt.time - 1), stringQuery)
                }

                return responseList
            } catch (e: TwitterException) {
                throw RestQueryException(e)
            }
        }
    }

    override fun getStreamFilter(): SNode = AndNode(
            ContainsNode(
                    VariableNode("receivedUsers"),
                    ValueNode(sourceAccount)
            ),
            ContainsNode(
                    VariableNode("text"),
                    ValueNode(parsedQuery.validQuery)
            )
    )
}