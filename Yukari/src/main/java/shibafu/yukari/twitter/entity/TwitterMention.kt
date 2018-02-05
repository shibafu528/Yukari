package shibafu.yukari.twitter.entity

import shibafu.yukari.entity.Mention
import twitter4j.UserMentionEntity

class TwitterMention : Mention {
    override val id: Long
    override val screenName: String

    constructor(entity: UserMentionEntity) {
        this.id = entity.id
        this.screenName = entity.screenName
    }

    constructor(id: Long, screenName: String) {
        this.id = id
        this.screenName = screenName
    }
}