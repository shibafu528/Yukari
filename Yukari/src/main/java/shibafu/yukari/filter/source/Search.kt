package shibafu.yukari.filter.source

import android.content.Context
import com.google.gson.Gson
import info.shibafu528.yukari.processor.filter.Source
import shibafu.yukari.database.Provider
import shibafu.yukari.entity.LoadMarker
import shibafu.yukari.entity.Status
import shibafu.yukari.filter.sexp.AndNode
import shibafu.yukari.filter.sexp.ContainsNode
import shibafu.yukari.filter.sexp.SNode
import shibafu.yukari.filter.sexp.ValueNode
import shibafu.yukari.filter.sexp.VariableNode
import shibafu.yukari.linkage.ProviderStream
import shibafu.yukari.linkage.RestQuery
import shibafu.yukari.linkage.RestQueryException
import shibafu.yukari.database.AuthUserRecord
import shibafu.yukari.twitter.TwitterStream
import shibafu.yukari.twitter.entity.TwitterStatus
import shibafu.yukari.twitter.streaming.FilterStream
import twitter4j.Query
import twitter4j.Twitter
import twitter4j.TwitterException
import java.util.*

/**
 * Twitterの検索APIを対象とする抽出ソースです。
 */
@Source(apiType = Provider.API_TWITTER, slug = "search")
data class Search(override val sourceAccount: AuthUserRecord, val query: String) : FilterSource {
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
                val responseList: MutableList<Status> = api.lookup(*ids)
                        .map { TwitterStatus(it, userRecord) }
                        .sortedByDescending { it.id }
                        .toMutableList()

                if (params.appendLoadMarker && result.hasNext() && responseList.isNotEmpty()) {
                    val last = responseList.last()
                    val stringQuery = gson.toJson(result.nextQuery())
                    responseList += LoadMarker(last.id - 1, Provider.API_TWITTER, last.id, userRecord, params.loadMarkerTag, Date(last.createdAt.time - 1000), stringQuery)
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

    override fun getDynamicChannelController() = object : DynamicChannelController {
        override fun isConnected(context: Context, stream: ProviderStream): Boolean {
            return FilterStream.getInstance(context, sourceAccount).contains(parsedQuery.validQuery)
        }

        override fun connect(context: Context, stream: ProviderStream) {
            stream as TwitterStream
            stream.startFilterStream(parsedQuery.validQuery, sourceAccount)
        }

        override fun disconnect(context: Context, stream: ProviderStream) {
            stream as TwitterStream
            stream.stopFilterStream(parsedQuery.validQuery, sourceAccount)
        }
    }
}