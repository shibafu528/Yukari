package shibafu.yukari.entity

import android.os.Parcel
import android.os.Parcelable
import com.google.gson.Gson

/**
 * 返信先の識別情報を格納するクラス。
 *
 * URL形式での表現を1つ持つことが必須要件で、Providerの仕様に応じて追加の識別情報を持たせても良い。
 */
class InReplyToId(val url: String) : Parcelable {
    private val perProviderId = HashMap<String, String>()

    constructor(parcel: Parcel) : this(parcel.readString().orEmpty()) {
        val perProviderIdSize = parcel.readInt()
        for (i in 0 until perProviderIdSize) {
            val key = parcel.readString() ?: continue
            val value = parcel.readString() ?: continue
            perProviderId[key] = value
        }
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(url)
        dest.writeInt(perProviderId.size)
        perProviderId.forEach { kv ->
            dest.writeString(kv.key)
            dest.writeString(kv.value)
        }
    }

    override fun describeContents(): Int = 0

    operator fun get(apiType: Int, host: String): String? = perProviderId["$apiType@$host"]

    operator fun set(apiType: Int, host: String, value: String) {
        perProviderId["$apiType@$host"] = value
    }

    fun toJson(gson: Gson = Gson()): String = gson.toJson(this)

    companion object {
        @JvmField val CREATOR = object : Parcelable.Creator<InReplyToId> {
            override fun createFromParcel(parcel: Parcel): InReplyToId {
                return InReplyToId(parcel)
            }

            override fun newArray(size: Int): Array<InReplyToId?> {
                return arrayOfNulls(size)
            }
        }

        /**
         * 過去のInReplyTo永続化との互換用。JSON Objectっぽい場合はJSONとしてデシリアライズして、それ以外の場合はとりあえずURLとして解釈する。
         */
        fun fromString(jsonOrString: String, gson: Gson = Gson()): InReplyToId {
            if (jsonOrString.startsWith("{")) {
                return gson.fromJson<InReplyToId>(jsonOrString, InReplyToId::class.java)
            } else {
                return InReplyToId(jsonOrString)
            }
        }
    }
}