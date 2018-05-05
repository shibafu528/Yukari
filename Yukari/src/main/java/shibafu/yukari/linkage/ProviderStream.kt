package shibafu.yukari.linkage

import shibafu.yukari.service.TwitterService
import shibafu.yukari.twitter.AuthUserRecord

/**
 * [shibafu.yukari.database.Provider] ごとの内部実装を持ったストリーミング通信管理の実装。
 */
interface ProviderStream {
    val channels: List<StreamChannel>

    fun onCreate(service: TwitterService)
    fun onStart()
    fun onDestroy()

    fun addUser(userRecord: AuthUserRecord): List<StreamChannel>
    fun removeUser(userRecord: AuthUserRecord)
}

/**
 * 1つのエンドポイントに対応するストリーミング通信管理の実装。
 */
interface StreamChannel {
    val channelId: String
    val userRecord: AuthUserRecord
    val allowUserControl: Boolean
    val isRunning: Boolean

    fun start()
    fun stop()
}