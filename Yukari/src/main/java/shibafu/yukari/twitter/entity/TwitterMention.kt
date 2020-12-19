package shibafu.yukari.twitter.entity

import shibafu.yukari.database.Provider
import shibafu.yukari.entity.Mention
import shibafu.yukari.database.AuthUserRecord
import shibafu.yukari.twitter.TwitterUtil
import twitter4j.UserMentionEntity

class TwitterMention : Mention {
    override val id: Long
    override val url: String
    override val screenName: String

    constructor(entity: UserMentionEntity) {
        this.id = entity.id
        this.url = TwitterUtil.getProfileUrl(entity.screenName)
        this.screenName = entity.screenName
    }

    constructor(id: Long, screenName: String) {
        this.id = id
        this.url = TwitterUtil.getProfileUrl(screenName)
        this.screenName = screenName
    }

    override fun isMentionedTo(userRecord: AuthUserRecord): Boolean {
        if (userRecord.Provider.apiType != Provider.API_TWITTER) {
            return false
        }

        return userRecord.ScreenName == screenName
    }
}