package shibafu.yukari.mastodon.entity

import com.sys1yagi.mastodon4j.api.entity.Mention
import shibafu.yukari.database.Provider
import shibafu.yukari.mastodon.MastodonUtil
import shibafu.yukari.database.AuthUserRecord
import shibafu.yukari.entity.Mention as IMention

class DonMention(mention: Mention) : IMention {
    override val id: Long = mention.id

    override val url: String = mention.url

    override val screenName: String = MastodonUtil.expandFullScreenName(mention.acct, mention.url)

    override fun isMentionedTo(userRecord: AuthUserRecord): Boolean {
        if (userRecord.Provider.apiType != Provider.API_MASTODON) {
            return false
        }

        return userRecord.ScreenName == screenName
    }
}