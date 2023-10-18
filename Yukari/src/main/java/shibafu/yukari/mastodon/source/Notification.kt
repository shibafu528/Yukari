package shibafu.yukari.mastodon.source

import com.sys1yagi.mastodon4j.MastodonClient
import com.sys1yagi.mastodon4j.api.Range
import com.sys1yagi.mastodon4j.api.method.Notifications
import info.shibafu528.yukari.processor.filter.Source
import shibafu.yukari.database.AuthUserRecord
import shibafu.yukari.database.Provider
import shibafu.yukari.filter.source.FilterSource
import shibafu.yukari.linkage.RestQuery
import shibafu.yukari.mastodon.AbstractMastodonRestQuery
import shibafu.yukari.mastodon.entity.DonNotification
import com.sys1yagi.mastodon4j.api.entity.Notification as MastodonNotification

@Source(apiType = Provider.API_MASTODON, slug = "notification")
data class Notification(override val sourceAccount: AuthUserRecord) : FilterSource {
    private val query = object : AbstractMastodonRestQuery<MastodonNotification, DonNotification>() {
        override fun resolve(client: MastodonClient, range: Range) = Notifications(client).getNotifications(range).execute()

        override fun mapToEntity(record: MastodonNotification, userRecord: AuthUserRecord) = DonNotification(record, userRecord)
    }

    override fun getRestQuery(): RestQuery = query
}