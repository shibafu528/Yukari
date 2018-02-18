package shibafu.yukari.linkage

import shibafu.yukari.entity.Status
import shibafu.yukari.twitter.AuthUserRecord
import java.util.*

/**
 * Created by shibafu on 2015/07/28.
 */
interface RestQuery {
    @Throws(RestQueryException::class)
    fun getRestResponses(userRecord: AuthUserRecord, api: Any, params: Params): List<Status>

    /**
     * @property maxId 取得するIDの最大として設定する値、負数の場合は設定せずにリクエストする
     * @property appendLoadMarker ページングのマーカーとして [shibafu.yukari.entity.LoadMarker] も配信するかどうか
     * @property loadMarkerTag ページングのマーカーに、どのクエリの続きを表しているのか識別するために付与するタグ
     * @property loadMarkerDate 1件も取得できなかった場合のリトライ用ページングマーカーに付与するタイムスタンプ
     */
    data class Params(
            var maxId: Long = -1,
            var limitCount: Int = 0,
            var appendLoadMarker: Boolean = true,
            var loadMarkerTag: String = "",
            var loadMarkerDate: Date = Date()
    )
}
