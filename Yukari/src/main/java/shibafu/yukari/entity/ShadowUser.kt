package shibafu.yukari.entity

import shibafu.yukari.twitter.AuthUserRecord

/**
 * 内部イベント処理用のUserスタブ
 */
class ShadowUser(userRecord: AuthUserRecord) : User {
    override val id: Long = userRecord.NumericId
    override val name: String = userRecord.Name
    override val screenName: String = userRecord.ScreenName
    override val isProtected: Boolean = false
    override val profileImageUrl: String = userRecord.ProfileImageUrl
    override val biggerProfileImageUrl: String = userRecord.ProfileImageUrl

    override fun isMentionedTo(userRecord: AuthUserRecord): Boolean {
        return false
    }
}