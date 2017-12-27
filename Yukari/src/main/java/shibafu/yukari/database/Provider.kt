package shibafu.yukari.database

import android.content.ContentValues
import android.database.Cursor

/**
 * 接続先情報
 */
@DBTable(CentralDatabase.TABLE_PROVIDERS)
class Provider : DBRecord {
    val id: Long
    val host: String
    val name: String
    val apiType: Int
    var consumerKey: String
    var consumerSecret: String

    constructor(host: String, name: String, apiType: Int, consumerKey: String, consumerSecret: String) {
        this.id = -1
        this.host = host
        this.name = name
        this.apiType = apiType
        this.consumerKey = consumerKey
        this.consumerSecret = consumerSecret
    }

    constructor(cursor: Cursor) {
        this.id = cursor.getLong(cursor.getColumnIndex(CentralDatabase.COL_PROVIDERS_ID))
        this.host = cursor.getString(cursor.getColumnIndex(CentralDatabase.COL_PROVIDERS_HOST))
        this.name = cursor.getString(cursor.getColumnIndex(CentralDatabase.COL_PROVIDERS_NAME))
        this.apiType = cursor.getInt(cursor.getColumnIndex(CentralDatabase.COL_PROVIDERS_API_TYPE))
        this.consumerKey = cursor.getString(cursor.getColumnIndex(CentralDatabase.COL_PROVIDERS_CONSUMER_KEY))
        this.consumerSecret = cursor.getString(cursor.getColumnIndex(CentralDatabase.COL_PROVIDERS_CONSUMER_SECRET))
    }

    override fun getContentValues(): ContentValues {
        val values = ContentValues()
        if (id > -1) {
            values.put(CentralDatabase.COL_PROVIDERS_ID, id)
        }
        values.put(CentralDatabase.COL_PROVIDERS_HOST, host)
        values.put(CentralDatabase.COL_PROVIDERS_NAME, name)
        values.put(CentralDatabase.COL_PROVIDERS_API_TYPE, apiType)
        values.put(CentralDatabase.COL_PROVIDERS_CONSUMER_KEY, consumerKey)
        values.put(CentralDatabase.COL_PROVIDERS_CONSUMER_SECRET, consumerSecret)
        return values
    }

    companion object {
        const val API_TWITTER = 0
        const val API_MASTODON = 1
    }
}