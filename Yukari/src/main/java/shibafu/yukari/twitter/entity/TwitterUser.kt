package shibafu.yukari.twitter.entity

import shibafu.yukari.database.Provider
import shibafu.yukari.entity.User
import shibafu.yukari.twitter.AuthUserRecord

class TwitterUser(val user: twitter4j.User) : User {
    override val id: Long
        get() = user.id

    override val url: String?
        get() = "https://twitter.com/$screenName"

    override val identicalUrl: String?
        get() = "https://twitter.com/intent/user?user_id=$id"

    override val host: String?
        get() = "twitter.com"

    override val name: String
        get() = user.name

    override val screenName: String
        get() = user.screenName

    override val isProtected: Boolean
        get() = user.isProtected

    override val profileImageUrl: String
        get() = user.profileImageURLHttps

    override val biggerProfileImageUrl: String
        get() = user.biggerProfileImageURLHttps

    override fun isMentionedTo(userRecord: AuthUserRecord): Boolean {
        if (userRecord.Provider.apiType != Provider.API_TWITTER) {
            return false
        }

        return userRecord.ScreenName == screenName
    }
}