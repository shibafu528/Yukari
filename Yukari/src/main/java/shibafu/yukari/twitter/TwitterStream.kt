package shibafu.yukari.twitter

import shibafu.yukari.database.AuthUserRecord
import shibafu.yukari.linkage.ProviderStream
import shibafu.yukari.linkage.StreamChannel
import shibafu.yukari.service.TwitterService

class TwitterStream : ProviderStream {
    override val channels: List<StreamChannel> = emptyList()

    override fun onCreate(service: TwitterService) {}

    override fun onStart() {}

    override fun onDestroy() {}

    override fun addUser(userRecord: AuthUserRecord): List<StreamChannel> = emptyList()

    override fun removeUser(userRecord: AuthUserRecord) {}
}
