package shibafu.yukari.database

import android.content.ContentValues
import android.database.Cursor
import java.io.Serializable

/**
 * [shibafu.yukari.linkage.StreamChannel] の状態記憶
 */
@DBTable(CentralDatabase.TABLE_STREAM_CHANNEL_STATES)
class StreamChannelState : DBRecord, Serializable {
    val id: Long
    val accountId: Long
    val channelId: String
    var isActive: Boolean

    constructor(accountId: Long, channelId: String, isActive: Boolean) {
        this.id = -1
        this.accountId = accountId
        this.channelId = channelId
        this.isActive = isActive
    }

    constructor(cursor: Cursor) {
        this.id = cursor.getLong(cursor.getColumnIndex(CentralDatabase.COL_STREAM_CHANNEL_STATES_ID))
        this.accountId = cursor.getLong(cursor.getColumnIndex(CentralDatabase.COL_STREAM_CHANNEL_STATES_ACCOUNT_ID))
        this.channelId = cursor.getString(cursor.getColumnIndex(CentralDatabase.COL_STREAM_CHANNEL_STATES_CHANNEL_ID))
        this.isActive = cursor.getInt(cursor.getColumnIndex(CentralDatabase.COL_STREAM_CHANNEL_STATES_IS_ACTIVE)) == 1
    }

    override fun getContentValues(): ContentValues {
        val values = ContentValues()
        if (id > -1) {
            values.put(CentralDatabase.COL_STREAM_CHANNEL_STATES_ID, id)
        }
        values.put(CentralDatabase.COL_STREAM_CHANNEL_STATES_ACCOUNT_ID, accountId)
        values.put(CentralDatabase.COL_STREAM_CHANNEL_STATES_CHANNEL_ID, channelId)
        values.put(CentralDatabase.COL_STREAM_CHANNEL_STATES_IS_ACTIVE, isActive)
        return values
    }
}