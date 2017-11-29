package shibafu.yukari.linkage

import shibafu.yukari.entity.Status

/**
 * Created by shibafu on 2015/07/28.
 */
interface RestQuery {
    @Throws(RestQueryException::class)
    fun getRestResponses(api: Any, maxId: Long, limitCount: Int, appendLoadMarker: Boolean, loadMarkerTag: String): List<Status>
}
