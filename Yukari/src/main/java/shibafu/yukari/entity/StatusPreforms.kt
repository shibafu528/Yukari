package shibafu.yukari.entity

import android.os.Parcel
import android.os.Parcelable
import org.eclipse.collections.api.map.primitive.MutableLongBooleanMap
import org.eclipse.collections.impl.map.mutable.primitive.LongBooleanHashMap
import java.io.Serializable

/**
 * 主に前処理の段階で決定しておく、ステータスのメタ情報など
 */
class StatusPreforms : Serializable, Parcelable {
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
    var favoritedUsers: MutableLongBooleanMap = LongBooleanHashMap() // TODO: 表示側は？

    /**
     * 繰り返し文の要約
     */
    var repeatedSequence: String? = null

    /**
     * このステータスが繰り返し文を含むかどうか
     */
    val isTooManyRepeatText: Boolean
        get() = repeatedSequence != null

    //<editor-fold desc="Parcelable">
    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        if (isCensoredThumbs) {
            dest.writeByte(1)
        } else {
            dest.writeByte(0)
        }
        dest.writeSerializable(repostRespondTo)
        dest.writeSerializable(favoritedUsers as Serializable)
        dest.writeString(repeatedSequence)
    }

    companion object {
        @JvmField val CREATOR = object : Parcelable.Creator<StatusPreforms> {
            override fun createFromParcel(source: Parcel?): StatusPreforms {
                source!!
                val sp = StatusPreforms()
                sp.isCensoredThumbs = source.readByte() == 1.toByte()
                sp.repostRespondTo = source.readSerializable() as Status?
                sp.favoritedUsers = source.readSerializable() as MutableLongBooleanMap
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