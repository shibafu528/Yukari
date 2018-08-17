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
     * @property limitCount 最大取得件数
     * @property appendLoadMarker ページングのマーカーとして [shibafu.yukari.entity.LoadMarker] も配信するかどうか
     * @property loadMarkerTag ページングのマーカーに、どのクエリの続きを表しているのか識別するために付与するタグ
     * @property loadMarkerDate 1件も取得できなかった場合のリトライ用ページングマーカーに付与するタイムスタンプ
     * @property stringCursor maxId, limitCount以外で必要なページング情報
     * @property longCursor maxId, limitCount以外で必要なページング情報
     */
    data class Params(
            var maxId: Long = -1,
            var limitCount: Int = 0,
            var appendLoadMarker: Boolean = true,
            var loadMarkerTag: String = "",
            var loadMarkerDate: Date = Date(),
            var stringCursor: String = "",
            var longCursor: Long = 0
    )
}
