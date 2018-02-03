package shibafu.yukari.twitter

import shibafu.yukari.database.Provider
import shibafu.yukari.entity.LoadMarker
import shibafu.yukari.entity.Status
import shibafu.yukari.linkage.RestQuery
import shibafu.yukari.linkage.RestQueryException
import shibafu.yukari.twitter.entity.TwitterStatus
import shibafu.yukari.twitter.statusimpl.PreformedStatus
import twitter4j.Paging
import twitter4j.ResponseList
import twitter4j.Twitter
import twitter4j.TwitterException

/**
 * RestQueryのTwitter用テンプレート
 */
class TwitterRestQuery(private val resolver: (Twitter, Paging) -> ResponseList<twitter4j.Status>) : RestQuery {
    override fun getRestResponses(userRecord: AuthUserRecord, api: Any, maxId: Long, limitCount: Int, appendLoadMarker: Boolean, loadMarkerTag: String): MutableList<Status> {
        api as Twitter
        val paging = Paging()
        paging.count = limitCount
        if (maxId > -1) {
            paging.maxId = maxId
        }
        try {
            // TODO: PreformedStatus挟まずにやりたい
            val responseList: MutableList<Status> = resolver(api, paging).map { TwitterStatus(PreformedStatus(it, userRecord), userRecord) }.toMutableList()

            if (appendLoadMarker) {
                responseList += if (responseList.isEmpty()) {
                    LoadMarker(maxId, Provider.API_TWITTER, maxId, userRecord, loadMarkerTag)
                } else {
                    val last = responseList.last()
                    LoadMarker(last.id - 1, Provider.API_TWITTER, last.id, userRecord, loadMarkerTag)
                }
            }

            return responseList
        } catch (e: TwitterException) {
            throw RestQueryException(e)
        }
    }
}