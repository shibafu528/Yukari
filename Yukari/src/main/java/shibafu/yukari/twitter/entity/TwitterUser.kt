package shibafu.yukari.twitter.entity

import shibafu.yukari.entity.User

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
}