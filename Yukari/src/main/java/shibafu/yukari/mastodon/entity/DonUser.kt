package shibafu.yukari.mastodon.entity

import android.net.Uri
import android.os.Parcel
import android.os.Parcelable
import com.google.gson.Gson
import com.sys1yagi.mastodon4j.api.entity.Account
import shibafu.yukari.database.Provider
import shibafu.yukari.entity.User
import shibafu.yukari.mastodon.MastodonUtil
import shibafu.yukari.database.AuthUserRecord

class DonUser(val account: Account?) : User, Parcelable {
    override val id: Long = account?.id ?: 0
    override val url: String? = account?.url
    override val host: String? = Uri.parse(account?.url).host
    override val name: String = account?.displayName.takeIf { !it.isNullOrEmpty() } ?: account?.userName ?: ""
    override val screenName: String = account?.let { MastodonUtil.expandFullScreenName(it.acct, it.url) } ?: ""
    override val isProtected: Boolean = account?.isLocked ?: false
    override val profileImageUrl: String = account?.avatar ?: ""
    override val biggerProfileImageUrl: String = account?.avatar ?: ""

    override fun isMentionedTo(userRecord: AuthUserRecord): Boolean {
        if (userRecord.Provider.apiType != Provider.API_MASTODON) {
            return false
        }

        return userRecord.ScreenName == screenName
    }

    //<editor-fold desc="Parcelable">
    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel?, flags: Int) {
        dest?.writeString(Gson().toJson(account))
    }

    companion object {
        @JvmField val CREATOR = object : Parcelable.Creator<DonUser> {
            override fun createFromParcel(source: Parcel?): DonUser {
                source!!
                val account = Gson().fromJson(source.readString(), Account::class.java)
                return DonUser(account)
            }

            override fun newArray(size: Int): Array<DonUser?> {
                return arrayOfNulls(size)
            }
        }
    }
    //</editor-fold>
}