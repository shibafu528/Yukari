package shibafu.yukari.mastodon.entity

import shibafu.yukari.database.AuthUserRecord

/**
 * 受信元Providerごとに固有のステータスIDを持つ [shibafu.yukari.entity.Status] が、その値を検索可能にするためのインターフェース。
 */
interface LocalStatusId {
    fun localStatusIdOrNull(userRecord: AuthUserRecord): Long?
}