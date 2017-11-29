package shibafu.yukari.linkage

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import android.support.v4.util.LongSparseArray
import android.util.Log
import android.widget.Toast
import com.sys1yagi.mastodon4j.api.exception.Mastodon4jRequestException
import shibafu.yukari.common.async.ParallelAsyncTask
import shibafu.yukari.twitter.AuthUserRecord
import twitter4j.Paging
import twitter4j.TwitterException
import shibafu.yukari.entity.Status as IStatus

/**
 * 非同期RESTリクエストの受付とAPI呼出の一元管理
 */
class StatusLoader(private val context: Context,
                   private val timelineHub: TimelineHub,
                   private val apiClientFactory: (AuthUserRecord) -> Any?) {
    private val sp: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    /** 実行中の非同期RESTリクエスト */
    private val workingRequests: LongSparseArray<ParallelAsyncTask<Void, Void, Void>> = LongSparseArray()

    /**
     * 非同期RESTリクエストを開始します。
     * @param restTag 通信結果の配信用タグ
     * @param userRecord 使用するアカウント
     * @param query RESTリクエストクエリ
     * @param pagingMaxId [Paging.maxId] に設定する値、負数の場合は設定しない
     * @param appendLoadMarker ページングのマーカーとして [shibafu.yukari.entity.LoadMarker] も配信するかどうか
     * @param loadMarkerTag ページングのマーカーに、どのクエリの続きを表しているのか識別するために付与するタグ
     * @return 開始された非同期処理に割り振ったキー。状態確認に使用できます。
     */
    fun requestRestQuery(restTag: String,
                         userRecord: AuthUserRecord,
                         query: RestQuery,
                         pagingMaxId: Long,
                         appendLoadMarker: Boolean,
                         loadMarkerTag: String): Long {
        val isNarrowMode = sp.getBoolean("pref_narrow", false)
        val taskKey = System.currentTimeMillis()
        val task = object : ParallelAsyncTask<Void, Void, Void>() {
            private var exception: RestQueryException? = null

            override fun doInBackground(vararg params: Void): Void? {
                Log.d("StatusLoader", String.format("Begin AsyncREST: @%s - %s -> %s", userRecord.ScreenName, restTag, query.javaClass.name))

                val api = apiClientFactory(userRecord) ?: return null

                try {
                    val limitCount = if (isNarrowMode) REQUEST_COUNT_NARROW else REQUEST_COUNT_NORMAL
                    val responseList = query.getRestResponses(api, pagingMaxId, limitCount, appendLoadMarker, loadMarkerTag)

                    if (isCancelled) return null

                    // StreamManagerに流す
                    for (status in responseList) {
                        timelineHub.onStatus(status, userRecord)
                    }

                    Log.d("StatusLoader", String.format("Received REST: @%s - %s - %d statuses", userRecord.ScreenName, restTag, responseList.size))
                } catch (e: RestQueryException) {
                    e.printStackTrace()
                    exception = e
                } finally {
                    timelineHub.onRestRequestCompleted(restTag, taskKey)
                }

                return null
            }

            override fun onPostExecute(result: Void) {
                workingRequests.remove(taskKey)

                val exception = this.exception?.cause ?: return
                when (exception) {
                    is TwitterException -> {
                        when (exception.statusCode) {
                            429 -> Toast.makeText(context,
                                    String.format("[@%s]\nレートリミット超過\n次回リセット: %d分%d秒後\n時間を空けて再度操作してください",
                                            userRecord.ScreenName,
                                            exception.rateLimitStatus.secondsUntilReset / 60,
                                            exception.rateLimitStatus.secondsUntilReset % 60),
                                    Toast.LENGTH_SHORT).show()
                            else -> {
                                val template: String
                                if (exception.isCausedByNetworkIssue) {
                                    template = "[@%s]\n通信エラー: %d:%d\n%s"
                                } else {
                                    template = "[@%s]\nエラー: %d:%d\n%s"
                                }
                                Toast.makeText(context,
                                        String.format(template,
                                                userRecord.ScreenName,
                                                exception.statusCode,
                                                exception.errorCode,
                                                exception.errorMessage),
                                        Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    is Mastodon4jRequestException -> {}
                }
            }
        }
        task.executeParallel()
        workingRequests.put(taskKey, task)
        Log.d("StatusManager", String.format("Requested REST: @%s - %s", userRecord.ScreenName, restTag))

        return taskKey
    }

    /**
     * 非同期RESTリクエストの実行状態を取得します。
     * @param taskKey [requestRestQuery] の戻り値
     * @return 実行中かつ中断されていなければ true
     */
    fun isRequestWorking(taskKey: Long): Boolean =
            workingRequests.get(taskKey) != null && !workingRequests.get(taskKey).isCancelled

    companion object {
        private const val REQUEST_COUNT_NORMAL = 100
        private const val REQUEST_COUNT_NARROW = 20
    }
}