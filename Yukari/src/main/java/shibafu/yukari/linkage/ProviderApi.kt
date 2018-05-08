package shibafu.yukari.linkage

import shibafu.yukari.entity.Status
import shibafu.yukari.service.TwitterService
import shibafu.yukari.twitter.AuthUserRecord

/**
 * [shibafu.yukari.database.Provider] ごとの内部実装を持った一般的な手続きの実装。
 */
interface ProviderApi {
    fun onCreate(service: TwitterService)
    fun onDestroy()

    fun createFavorite(userRecord: AuthUserRecord, status: Status): Boolean
    fun destroyFavorite(userRecord: AuthUserRecord, status: Status): Boolean

    fun repostStatus(userRecord: AuthUserRecord, status: Status): Boolean

    fun destroyStatus(userRecord: AuthUserRecord, status: Status): Boolean
}