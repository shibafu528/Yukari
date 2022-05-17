package shibafu.yukari.linkage

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import androidx.collection.LongSparseArray
import android.util.Log
import android.widget.Toast
import com.sys1yagi.mastodon4j.api.exception.Mastodon4jRequestException
import shibafu.yukari.common.async.ParallelAsyncTask
import shibafu.yukari.database.AuthUserRecord
import twitter4j.TwitterException

/**
 * 非同期RESTリクエストの受付とAPI呼出の一元管理
 */
class StatusLoader(private val context: Context,
                   private val timelineHub: TimelineHub,
                   private val apiClientFactory: (AuthUserRecord) -> Any?) {
    private val sp: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    /** 実行中の非同期RESTリクエスト */
    private val workingRequests: LongSparseArray<ParallelAsyncTask<Void?, Void?, Void?>> = LongSparseArray()

    /**
     * 非同期RESTリクエストを開始します。
     * @param timelineId 通信結果の配信先識別子
     * @param userRecord 使用するアカウント
     * @param query RESTリクエストクエリ
     * @param params [RestQuery.Params]
     * @return 開始された非同期処理に割り振ったキー。状態確認に使用できます。
     */
    @SuppressLint("StaticFieldLeak")
    fun requestRestQuery(timelineId: String,
                         userRecord: AuthUserRecord,
                         query: RestQuery,
                         params: RestQuery.Params): Long {
        val taskKey = System.currentTimeMillis()
        val task = object : ParallelAsyncTask<Void?, Void?, Void?>() {
            private var exception: RestQueryException? = null

            override fun doInBackground(vararg p: Void?): Void? {
                Log.d("StatusLoader", String.format("Begin AsyncREST: @%s - %s -> %s", userRecord.ScreenName, timelineId, query.javaClass.name))

                val api = apiClientFactory(userRecord) ?: return null

                try {
                    params.limitCount = REQUEST_COUNT_NORMAL

                    val responseList = query.getRestResponses(userRecord, api, params)

                    if (isCancelled) return null

                    // StreamManagerに流す
                    for (status in responseList) {
                        timelineHub.onStatus(timelineId, status, false)
                    }

                    Log.d("StatusLoader", String.format("Received REST: @%s - %s - %d statuses", userRecord.ScreenName, timelineId, responseList.size))
                } catch (e: RestQueryException) {
                    e.printStackTrace()
                    exception = e
                } finally {
                    timelineHub.onRestRequestCompleted(timelineId, taskKey)
                }

                return null
            }

            override fun onPostExecute(result: Void?) {
                workingRequests.delete(taskKey)

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
                            403 -> {
                                if (exception.errorCode == 93) {
                                    Toast.makeText(context,
                                            String.format("[@%s]\nアクセス権エラー: %d:%d\n" +
                                                    "DMへのアクセスが制限されています。\n" +
                                                    "一度アプリ連携を切って認証を再発行してみてください。",
                                                    userRecord.ScreenName,
                                                    exception.errorCode,
                                                    exception.errorMessage),
                                            Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context,
                                            String.format("[@%s]\nアクセス権エラー: %d:%d\n%s",
                                                    userRecord.ScreenName,
                                                    exception.statusCode,
                                                    exception.errorCode,
                                                    exception.errorMessage),
                                            Toast.LENGTH_SHORT).show()
                                }
                            }
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

            override fun onCancelled() {
                workingRequests.delete(taskKey)
                timelineHub.onRestRequestCancelled(timelineId, taskKey)
            }
        }
        task.executeParallel()
        workingRequests.put(taskKey, task)
        Log.d("StatusManager", String.format("Requested REST: @%s - %s", userRecord.ScreenName, timelineId))

        return taskKey
    }

    /**
     * 非同期RESTリクエストの実行状態を取得します。
     * @param taskKey [requestRestQuery] の戻り値
     * @return 実行中かつ中断されていなければ true
     */
    fun isRequestWorking(taskKey: Long): Boolean =
            workingRequests.get(taskKey)?.let { !it.isCancelled } ?: false

    /**
     * 実行中の全てのリクエストをキャンセルします。
     */
    fun cancelAll() {
        for (i in 0 until workingRequests.size()) {
            val task = workingRequests[workingRequests.keyAt(i)]
            if (task != null && !task.isCancelled) {
                task.cancel(true)
            }
        }
    }

    companion object {
        private const val REQUEST_COUNT_NORMAL = 100
        private const val REQUEST_COUNT_NARROW = 20
    }
}