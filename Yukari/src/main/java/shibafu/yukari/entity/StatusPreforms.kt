package shibafu.yukari.entity

import android.os.Parcel
import android.os.Parcelable
import androidx.collection.MutableLongSet
import java.io.Serializable

/**
 * 主に前処理の段階で決定しておく、ステータスのメタ情報など
 */
class StatusPreforms : Serializable, Parcelable, Cloneable {
    /**
     * 表示すべきでないメディアを含んでいるかどうか
     */
    var isCensoredThumbs: Boolean = false

    /**
     * RTレスポンスの対象ステータス (このステータスが、どのステータスに関連したと思われるものなのか)
     */
    var repostRespondTo: Status? = null

    /**
     * お気に入り登録
     *
     * ここに入れていいのは [shibafu.yukari.database.AuthUserRecord.InternalId] のみ。
     */
    var favoritedUsers = MutableLongSet() // TODO: 表示側は？

    /**
     * 繰り返し文の要約
     */
    var repeatedSequence: String? = null

    /**
     * このステータスが繰り返し文を含むかどうか
     */
    val isTooManyRepeatText: Boolean
        get() = repeatedSequence != null

    public override fun clone(): StatusPreforms {
        val sp = super.clone() as StatusPreforms
        sp.favoritedUsers = MutableLongSet(favoritedUsers.size)
        sp.favoritedUsers += favoritedUsers
        return sp
    }

    //<editor-fold desc="Parcelable">
    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        if (isCensoredThumbs) {
            dest.writeByte(1)
        } else {
            dest.writeByte(0)
        }
        dest.writeSerializable(repostRespondTo)
        dest.writeInt(favoritedUsers.size)
        favoritedUsers.forEach { dest.writeLong(it) }
        dest.writeString(repeatedSequence)
    }

    companion object {
        @JvmField val CREATOR = object : Parcelable.Creator<StatusPreforms> {
            override fun createFromParcel(source: Parcel?): StatusPreforms {
                source!!
                val sp = StatusPreforms()
                sp.isCensoredThumbs = source.readByte() == 1.toByte()
                sp.repostRespondTo = source.readSerializable() as Status?
                sp.favoritedUsers = MutableLongSet()
                repeat(source.readInt()) { sp.favoritedUsers += source.readLong() }
                sp.repeatedSequence = source.readString()
                return sp
            }

            override fun newArray(size: Int): Array<StatusPreforms?> {
                return arrayOfNulls(size)
            }
        }
    }
    //</editor-fold>
}