package shibafu.yukari.database

import android.content.ContentValues
import android.database.Cursor
import java.io.Serializable

@DBTable(CentralDatabase.TABLE_MUTE)
class MuteConfig : DBRecord, Serializable {
    val id: Long
    /** 検査対象 */
    var scope: Int
    /** マッチング方法 */
    var match: Int
    /** ミュート対象 */
    var mute: Int
    /** 検査クエリ */
    var query: String
    /** 有効期限 */
    var expirationTimeMillis: Long

    val isTimeLimited: Boolean
        get() = expirationTimeMillis > 0

    @JvmOverloads
    constructor(scope: Int, match: Int, mute: Int, query: String, expirationTimeMillis: Long = 0) {
        this.id = -1
        this.scope = scope
        this.match = match
        this.mute = mute
        this.query = query
        this.expirationTimeMillis = expirationTimeMillis
    }

    constructor(cursor: Cursor) {
        id = cursor.getLong(cursor.getColumnIndex(CentralDatabase.COL_MUTE_ID))
        scope = cursor.getInt(cursor.getColumnIndex(CentralDatabase.COL_MUTE_SCOPE))
        match = cursor.getInt(cursor.getColumnIndex(CentralDatabase.COL_MUTE_MATCH))
        mute = cursor.getInt(cursor.getColumnIndex(CentralDatabase.COL_MUTE_MUTE))
        query = cursor.getString(cursor.getColumnIndex(CentralDatabase.COL_MUTE_QUERY))
        expirationTimeMillis = cursor.getLong(cursor.getColumnIndex(CentralDatabase.COL_MUTE_EXPIRATION_TIME_MILLIS))
    }

    fun expired(): Boolean {
        return isTimeLimited && expirationTimeMillis < System.currentTimeMillis()
    }

    override fun getContentValues(): ContentValues {
        val values = ContentValues()
        if (id > -1) {
            values.put(CentralDatabase.COL_MUTE_ID, id)
        }
        values.put(CentralDatabase.COL_MUTE_SCOPE, scope)
        values.put(CentralDatabase.COL_MUTE_MATCH, match)
        values.put(CentralDatabase.COL_MUTE_MUTE, mute)
        values.put(CentralDatabase.COL_MUTE_QUERY, query)
        values.put(CentralDatabase.COL_MUTE_EXPIRATION_TIME_MILLIS, expirationTimeMillis)
        return values
    }

    companion object {
        const val SCOPE_TEXT = 0
        const val SCOPE_USER_NAME = 1
        const val SCOPE_USER_SN = 2
        const val SCOPE_USER_ID = 3
        const val SCOPE_VIA = 4

        const val MUTE_TWEET = 0
        const val MUTE_TWEET_RTED = 1
        const val MUTE_RETWEET = 2
        const val MUTE_NOTIF_FAV = 3
        const val MUTE_NOTIF_RT = 4
        const val MUTE_NOTIF_MENTION = 5
        const val MUTE_IMAGE_THUMB = 6
    }
}