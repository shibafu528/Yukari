package shibafu.yukari.linkage

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import shibafu.yukari.entity.Status
import shibafu.yukari.service.TwitterService

/**
 * [Status] の配信管理
 */
class TimelineHub(private val service: TwitterService) {
    private val context: Context = service.applicationContext
    private val sp: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    /**
     * [Status] の受信
     */
    fun onStatus(status: Status) {

    }

    /**
     * [StatusLoader.requestRestQuery] の処理完了通知の受信
     */
    fun onRestRequestCompleted(restTag: String, taskKey: Long) {

    }
}