package shibafu.yukari.database

import android.content.ContentValues
import android.database.Cursor
import shibafu.yukari.entity.Status
import shibafu.yukari.twitter.entity.TwitterStatus
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util.*

@DBTable(CentralDatabase.TABLE_BOOKMARKS)
class Bookmark private constructor(val twitterStatus: TwitterStatus, private val saveDate: Date) : Status by twitterStatus, DBRecord {
    constructor(status: TwitterStatus) : this(status, Date())

    constructor(cursor: Cursor) : this(
            byteArrayToStatus(cursor.getBlob(cursor.getColumnIndex(CentralDatabase.COL_BOOKMARKS_BLOB)), AuthUserRecord(cursor)),
            Date(cursor.getLong(cursor.getColumnIndex(CentralDatabase.COL_BOOKMARKS_SAVE_DATE)))
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Bookmark) return false

        return this.id == other.id
    }

    override fun hashCode(): Int {
        return twitterStatus.hashCode()
    }

    // あえてoverrideしておかないとdelegateされてバグる
    @Suppress("RedundantOverride")
    override fun merge(status: Status): Status {
        return super.merge(status)
    }

    override fun getContentValues(): ContentValues {
        val values = ContentValues()
        values.put(CentralDatabase.COL_BOOKMARKS_ID, twitterStatus.id)
        values.put(CentralDatabase.COL_BOOKMARKS_RECEIVER_ID, representUser.InternalId)
        values.put(CentralDatabase.COL_BOOKMARKS_SAVE_DATE, saveDate.time)
        values.put(CentralDatabase.COL_BOOKMARKS_BLOB, statusToByteArray())
        return values
    }

    private fun statusToByteArray(): ByteArray {
        val baos = ByteArrayOutputStream()
        val oos = ObjectOutputStream(baos)
        oos.writeObject(twitterStatus.status)
        oos.close()
        return baos.toByteArray()
    }

    companion object {
        private fun byteArrayToStatus(byteArray: ByteArray, representUser: AuthUserRecord): TwitterStatus {
            val bais = ByteArrayInputStream(byteArray)
            val ois = ObjectInputStream(bais)
            val status = ois.readObject() as twitter4j.Status
            return TwitterStatus(status, representUser)
        }
    }

    /**
     * インポート・エクスポート時の型としてのみ使用。いずれ廃止して、Bookmarkクラスを参照するべき。
     */
    class SerializeEntity
}