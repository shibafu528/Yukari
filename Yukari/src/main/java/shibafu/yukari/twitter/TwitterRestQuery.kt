package shibafu.yukari.twitter

import shibafu.yukari.database.Provider
import shibafu.yukari.entity.LoadMarker
import shibafu.yukari.entity.Status
import shibafu.yukari.linkage.RestQuery
import shibafu.yukari.linkage.RestQueryException
import shibafu.yukari.twitter.entity.TwitterStatus
import twitter4j.Paging
import twitter4j.ResponseList
import twitter4j.Twitter
import twitter4j.TwitterException
import java.util.*

/**
 * RestQueryのTwitter用テンプレート
 */
class TwitterRestQuery(private val resolver: (Twitter, Paging) -> ResponseList<twitter4j.Status>) : RestQuery {
    override fun getRestResponses(userRecord: AuthUserRecord, api: Any, params: RestQuery.Params): MutableList<Status> {
        api as Twitter
        val paging = Paging()
        paging.count = params.limitCount
        if (params.maxId > -1) {
            paging.maxId = params.maxId
        }
        try {
            val responseList: MutableList<Status> = resolver(api, paging).map { TwitterStatus(it, userRecord) }.toMutableList()

            if (params.appendLoadMarker) {
                responseList += if (responseList.isEmpty()) {
                    LoadMarker(params.maxId, Provider.API_TWITTER, params.maxId, userRecord, params.loadMarkerTag, params.loadMarkerDate)
                } else {
                    val last = responseList.last()
                    LoadMarker(last.id - 1, Provider.API_TWITTER, last.id, userRecord, params.loadMarkerTag, Date(last.createdAt.time - 1))
                }
            }

            return responseList
        } catch (e: TwitterException) {
            throw RestQueryException(e)
        }
    }
}