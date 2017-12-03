package shibafu.yukari.linkage

import shibafu.yukari.entity.Status
import shibafu.yukari.twitter.AuthUserRecord

/**
 * Created by shibafu on 2015/07/28.
 */
interface RestQuery {
    @Throws(RestQueryException::class)
    fun getRestResponses(userRecord: AuthUserRecord, api: Any, maxId: Long, limitCount: Int, appendLoadMarker: Boolean, loadMarkerTag: String): List<Status>
}
