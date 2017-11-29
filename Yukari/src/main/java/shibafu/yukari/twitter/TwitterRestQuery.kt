package shibafu.yukari.twitter

import shibafu.yukari.entity.LoadMarker
import shibafu.yukari.entity.Status
import shibafu.yukari.linkage.RestQuery
import shibafu.yukari.linkage.RestQueryException
import shibafu.yukari.twitter.entity.TwitterStatus
import twitter4j.Paging
import twitter4j.ResponseList
import twitter4j.Twitter
import twitter4j.TwitterException

/**
 * RestQueryのTwitter用テンプレート
 */
class TwitterRestQuery(private val resolver: (Twitter, Paging) -> ResponseList<twitter4j.Status>) : RestQuery {
    override fun getRestResponses(api: Any, maxId: Long, limitCount: Int, appendLoadMarker: Boolean, loadMarkerTag: String): MutableList<Status> {
        api as Twitter
        val paging = Paging()
        paging.maxId = maxId
        paging.count = limitCount
        try {
            val responseList: MutableList<Status> = resolver(api, paging).map { TwitterStatus(it) }.toMutableList()

            if (appendLoadMarker) {
                responseList += if (responseList.isEmpty()) {
                    LoadMarker(maxId, "twitter.com", maxId, api.id, loadMarkerTag)
                } else {
                    val last = responseList.last()
                    LoadMarker(last.id - 1, "twitter.com", last.id, api.id, loadMarkerTag)
                }
            }

            return responseList
        } catch (e: TwitterException) {
            throw RestQueryException(e)
        }
    }
}