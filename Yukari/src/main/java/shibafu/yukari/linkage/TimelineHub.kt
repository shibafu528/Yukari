package shibafu.yukari.linkage

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import shibafu.yukari.entity.Status
import shibafu.yukari.service.TwitterService
import shibafu.yukari.twitter.AuthUserRecord

/**
 * [Status] の配信管理
 */
class TimelineHub(private val service: TwitterService) {
    private val context: Context = service.applicationContext
    private val sp: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    fun onStatus(status: Status, userRecord: AuthUserRecord) {

    }

    fun onRestRequestCompleted(restTag: String, taskKey: Long) {

    }
}