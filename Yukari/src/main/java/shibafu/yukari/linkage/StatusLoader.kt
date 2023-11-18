package shibafu.yukari.linkage

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.preference.PreferenceManager
import android.util.Log
import androidx.collection.LongSparseArray
import com.google.common.util.concurrent.ThreadFactoryBuilder
import shibafu.yukari.database.AuthUserRecord
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * 非同期RESTリクエストの受付とAPI呼出の一元管理
 */
class StatusLoader(private val context: Context,
                   private val timelineHub: TimelineHub,
                   private val apiClientFactory: (AuthUserRecord) -> Any?) {
    private val sp: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    private val executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(), ThreadFactoryBuilder().setNameFormat("StatusLoader-%d").build())

    /** 実行中の非同期RESTリクエスト */
    private val workingRequests: LongSparseArray<Future<Unit>> = LongSparseArray()

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
        val taskKey = SystemClock.elapsedRealtime()
        val future = executor.submit<Unit> {
            try {
                Log.d("StatusLoader", String.format("Begin AsyncREST: @%s - %s -> %s", userRecord.ScreenName, timelineId, query.javaClass.name))

                try {
                    val api = apiClientFactory(userRecord) ?: return@submit
                    params.limitCount = REQUEST_COUNT_NORMAL

                    val responseList = query.getRestResponses(userRecord, api, params)

                    if (Thread.interrupted()) throw InterruptedException()

                    // StreamManagerに流す
                    for (status in responseList) {
                        timelineHub.onStatus(timelineId, status, false)
                    }

                    Log.d("StatusLoader", String.format("Received REST: @%s - %s - %d statuses", userRecord.ScreenName, timelineId, responseList.size))
                } catch (e: RestQueryException) {
                    e.printStackTrace()
                    timelineHub.onRestRequestFailure(timelineId, taskKey, e)
                } finally {
                    timelineHub.onRestRequestSuccess(timelineId, taskKey)
                }
            } catch (e: InterruptedException) {
                e.printStackTrace()
            } finally {
                workingRequests.remove(taskKey)
            }
        }
        workingRequests.put(taskKey, future)
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