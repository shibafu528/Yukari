package shibafu.yukari.mastodon

import com.sys1yagi.mastodon4j.MastodonClient
import com.sys1yagi.mastodon4j.api.Pageable
import com.sys1yagi.mastodon4j.api.Range
import shibafu.yukari.database.AuthUserRecord
import shibafu.yukari.mastodon.entity.DonStatus
import com.sys1yagi.mastodon4j.api.entity.Status as MastodonStatus

/**
 * [AbstractMastodonRestQuery] の [DonStatus] 特化版
 */
class MastodonRestQuery(private val resolver: (MastodonClient, Range) -> Pageable<MastodonStatus>) : AbstractMastodonRestQuery<MastodonStatus, DonStatus>() {
    override fun resolve(client: MastodonClient, range: Range) = resolver(client, range)

    override fun mapToEntity(record: MastodonStatus, userRecord: AuthUserRecord) = DonStatus(record, userRecord)
}