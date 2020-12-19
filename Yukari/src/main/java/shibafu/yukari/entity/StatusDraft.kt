package shibafu.yukari.entity

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Parcel
import android.os.Parcelable
import shibafu.yukari.activity.TweetActivity
import shibafu.yukari.database.CentralDatabase
import shibafu.yukari.database.AuthUserRecord
import twitter4j.GeoLocation

/**
 * 投稿の下書きデータ
 */
data class StatusDraft(
        var writers: ArrayList<AuthUserRecord> = ArrayList(),
        var text: String = "",
        var dateTime: Long = System.currentTimeMillis(),
        var inReplyTo: InReplyToId? = null,
        var isQuoted: Boolean = false,
        var attachPictures: ArrayList<Uri> = ArrayList(),
        var useGeoLocation: Boolean = false,
        var geoLatitude: Double = 0.0,
        var geoLongitude: Double = 0.0,
        var isPossiblySensitive: Boolean = false,
        var isDirectMessage: Boolean = false,
        var isFailedDelivery: Boolean = false,
        var messageTarget: String? = null,
        var visibility: Visibility = Visibility.PUBLIC,
        var spoilerText: String? = null
) : Parcelable {
    constructor(cursor: Cursor) : this(
            text = cursor.getString(cursor.getColumnIndex(CentralDatabase.COL_DRAFTS_TEXT)),
            dateTime = cursor.getLong(cursor.getColumnIndex(CentralDatabase.COL_DRAFTS_DATETIME)),
            inReplyTo = cursor.getString(cursor.getColumnIndex(CentralDatabase.COL_DRAFTS_IN_REPLY_TO))?.let { InReplyToId.fromString(it) },
            isQuoted = cursor.getInt(cursor.getColumnIndex(CentralDatabase.COL_DRAFTS_IS_QUOTED)) == 1,
            useGeoLocation = cursor.getInt(cursor.getColumnIndex(CentralDatabase.COL_DRAFTS_USE_GEO_LOCATION)) == 1,
            geoLatitude = cursor.getDouble(cursor.getColumnIndex(CentralDatabase.COL_DRAFTS_GEO_LATITUDE)),
            geoLongitude = cursor.getDouble(cursor.getColumnIndex(CentralDatabase.COL_DRAFTS_GEO_LONGITUDE)),
            isPossiblySensitive = cursor.getInt(cursor.getColumnIndex(CentralDatabase.COL_DRAFTS_IS_POSSIBLY_SENSITIVE)) == 1,
            isDirectMessage = cursor.getInt(cursor.getColumnIndex(CentralDatabase.COL_DRAFTS_IS_DIRECT_MESSAGE)) == 1,
            isFailedDelivery = cursor.getInt(cursor.getColumnIndex(CentralDatabase.COL_DRAFTS_IS_FAILED_DELIVERY)) == 1,
            messageTarget = cursor.getString(cursor.getColumnIndex(CentralDatabase.COL_DRAFTS_MESSAGE_TARGET)),
            visibility = Visibility.values()[cursor.getInt(cursor.getColumnIndex(CentralDatabase.COL_DRAFTS_VISIBILITY))],
            spoilerText = cursor.getString(cursor.getColumnIndex(CentralDatabase.COL_DRAFTS_SPOILER_TEXT))
    ) {
        val attachedPictureString = cursor.getString(cursor.getColumnIndex(CentralDatabase.COL_DRAFTS_ATTACHED_PICTURE))
        if (!attachedPictureString.isNullOrEmpty()) {
            attachedPictureString.split("|").forEach {
                attachPictures.add(Uri.parse(it))
            }
        }
    }

    fun getContentValuesArray(): Array<ContentValues> {
        return writers.map { writer ->
            val values = ContentValues()
            values.put(CentralDatabase.COL_DRAFTS_WRITER_ID, writer.InternalId)
            values.put(CentralDatabase.COL_DRAFTS_DATETIME, dateTime)
            values.put(CentralDatabase.COL_DRAFTS_TEXT, text)
            values.put(CentralDatabase.COL_DRAFTS_IN_REPLY_TO, inReplyTo?.toJson())
            values.put(CentralDatabase.COL_DRAFTS_IS_QUOTED, isQuoted)
            if (attachPictures.isNotEmpty()) {
                val pictures = buildString {
                    attachPictures.forEach {
                        if (this.isNotEmpty()) append("|")
                        append(it.toString())
                    }
                }
                values.put(CentralDatabase.COL_DRAFTS_ATTACHED_PICTURE, pictures)
            }
            values.put(CentralDatabase.COL_DRAFTS_USE_GEO_LOCATION, useGeoLocation)
            values.put(CentralDatabase.COL_DRAFTS_GEO_LATITUDE, geoLatitude)
            values.put(CentralDatabase.COL_DRAFTS_GEO_LONGITUDE, geoLongitude)
            values.put(CentralDatabase.COL_DRAFTS_IS_POSSIBLY_SENSITIVE, isPossiblySensitive)
            values.put(CentralDatabase.COL_DRAFTS_IS_DIRECT_MESSAGE, isDirectMessage)
            values.put(CentralDatabase.COL_DRAFTS_IS_FAILED_DELIVERY, isFailedDelivery)
            values.put(CentralDatabase.COL_DRAFTS_MESSAGE_TARGET, messageTarget)
            values.put(CentralDatabase.COL_DRAFTS_VISIBILITY, visibility.ordinal)
            values.put(CentralDatabase.COL_DRAFTS_SPOILER_TEXT, spoilerText)
            values
        }.toTypedArray()
    }

    fun getTweetIntent(context: Context): Intent {
        val intent = Intent(context, TweetActivity::class.java)
        intent.putExtra(TweetActivity.EXTRA_TEXT, text)
        intent.putExtra(TweetActivity.EXTRA_MEDIA, ArrayList(attachPictures.map { it.toString() }))
        intent.putExtra(TweetActivity.EXTRA_WRITERS, writers)
        if (isDirectMessage) {
            intent.putExtra(TweetActivity.EXTRA_MODE, TweetActivity.MODE_DM)
            intent.putExtra(TweetActivity.EXTRA_IN_REPLY_TO, inReplyTo?.url)
            intent.putExtra(TweetActivity.EXTRA_DM_TARGET_SN, messageTarget)
        } else if (inReplyTo != null) {
            intent.putExtra(TweetActivity.EXTRA_MODE, TweetActivity.MODE_REPLY)
            intent.putExtra(TweetActivity.EXTRA_IN_REPLY_TO, inReplyTo?.url) // TODO: temporary hack
        } else {
            intent.putExtra(TweetActivity.EXTRA_MODE, TweetActivity.MODE_TWEET)
        }
        if (useGeoLocation) {
            intent.putExtra(TweetActivity.EXTRA_GEO_LOCATION, GeoLocation(geoLatitude, geoLongitude))
        }
        intent.putExtra(TweetActivity.EXTRA_VISIBILITY, visibility.ordinal)
        intent.putExtra(TweetActivity.EXTRA_SPOILER_TEXT, spoilerText)
        intent.putExtra(TweetActivity.EXTRA_DRAFT, this)
        return intent
    }

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel?, flags: Int) {
        dest?.let {
            dest.writeList(writers.toList())
            dest.writeString(text)
            dest.writeLong(dateTime)
            dest.writeParcelable(inReplyTo, flags)
            dest.writeByte(if (isQuoted) 1 else 0)
            dest.writeTypedList(attachPictures)
            dest.writeByte(if (useGeoLocation) 1 else 0)
            dest.writeDouble(geoLatitude)
            dest.writeDouble(geoLongitude)
            dest.writeByte(if (isPossiblySensitive) 1 else 0)
            dest.writeByte(if (isDirectMessage) 1 else 0)
            dest.writeByte(if (isFailedDelivery) 1 else 0)
            dest.writeString(messageTarget)
            dest.writeInt(visibility.ordinal)
            dest.writeString(spoilerText)
        }
    }

    fun copyForJava() = this.copy()

    enum class Visibility {
        PUBLIC,
        UNLISTED,
        PRIVATE,
        DIRECT
    }

    companion object {
        @JvmField val CREATOR = object : Parcelable.Creator<StatusDraft> {
            override fun createFromParcel(source: Parcel): StatusDraft {
                return StatusDraft(
                        source.readArrayList(this.javaClass.classLoader) as ArrayList<AuthUserRecord>,
                        source.readString(),
                        source.readLong(),
                        source.readParcelable(InReplyToId::class.java.classLoader),
                        source.readByte() == 1.toByte(),
                        source.createTypedArrayList(Uri.CREATOR),
                        source.readByte() == 1.toByte(),
                        source.readDouble(),
                        source.readDouble(),
                        source.readByte() == 1.toByte(),
                        source.readByte() == 1.toByte(),
                        source.readByte() == 1.toByte(),
                        source.readString(),
                        Visibility.values()[source.readInt()],
                        source.readString()
                )
            }

            override fun newArray(size: Int): Array<StatusDraft?> {
                return arrayOfNulls(size)
            }
        }
    }
}