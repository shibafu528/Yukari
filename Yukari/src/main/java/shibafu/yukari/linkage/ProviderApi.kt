package shibafu.yukari.linkage

import shibafu.yukari.entity.Status
import shibafu.yukari.entity.StatusDraft
import shibafu.yukari.service.TwitterService
import shibafu.yukari.twitter.AuthUserRecord
import java.io.File

/**
 * [shibafu.yukari.database.Provider] ごとの内部実装を持った一般的な手続きの実装。
 */
interface ProviderApi {
    fun onCreate(service: TwitterService)
    fun onDestroy()

    fun getApiClient(userRecord: AuthUserRecord?): Any?

    fun createFavorite(userRecord: AuthUserRecord, status: Status): Boolean
    fun destroyFavorite(userRecord: AuthUserRecord, status: Status): Boolean

    @Throws(ProviderApiException::class)
    fun postStatus(userRecord: AuthUserRecord, draft: StatusDraft, mediaList: List<File>): Status
    fun repostStatus(userRecord: AuthUserRecord, status: Status): Boolean
    fun destroyStatus(userRecord: AuthUserRecord, status: Status): Boolean

    @Throws(ProviderApiException::class)
    fun showStatus(userRecord: AuthUserRecord, url: String): Status
}

/**
 * [ProviderApi] 内部で発生した例外のラッパー
 */
class ProviderApiException(message: String? = null, cause: Throwable? = null) : Exception(message, cause)