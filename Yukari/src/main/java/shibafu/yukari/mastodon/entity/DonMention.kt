package shibafu.yukari.mastodon.entity

import com.sys1yagi.mastodon4j.api.entity.Mention
import shibafu.yukari.mastodon.MastodonUtil
import shibafu.yukari.entity.Mention as IMention

class DonMention(mention: Mention) : IMention {
    override val id: Long = mention.id

    override val screenName: String = MastodonUtil.expandFullScreenName(mention.acct, mention.url)
}