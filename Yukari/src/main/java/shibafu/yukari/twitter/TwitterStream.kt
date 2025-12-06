package shibafu.yukari.twitter

import android.content.Context
import shibafu.yukari.database.AuthUserRecord
import shibafu.yukari.linkage.ProviderStream
import shibafu.yukari.linkage.StreamChannel

class TwitterStream : ProviderStream {
    override val channels: List<StreamChannel> = emptyList()

    override fun onCreate(context: Context) {}

    override fun onStart() {}

    override fun onDestroy() {}

    override fun addUser(userRecord: AuthUserRecord): List<StreamChannel> = emptyList()

    override fun removeUser(userRecord: AuthUserRecord) {}
}
