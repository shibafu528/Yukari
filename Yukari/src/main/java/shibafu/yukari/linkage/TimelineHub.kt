package shibafu.yukari.linkage

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import shibafu.yukari.entity.Status
import shibafu.yukari.service.TwitterService
import shibafu.yukari.util.putDebugLog
import java.util.*
import java.util.concurrent.LinkedBlockingQueue

/**
 * [Status] の配信管理
 */
class TimelineHub(private val service: TwitterService) {
    private val context: Context = service.applicationContext
    private val sp: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    // TLオブザーバとキュー (TODO: こいつら同期処理が必要だったはずだけど、うまいことやれないか？)
    private val observers: MutableList<TimelineObserver> = arrayListOf()
    private val eventQueues: MutableMap<String, Queue<TimelineEvent>> = hashMapOf()

    /**
     * タイムラインオブザーバの登録
     * @param observer 登録したいオブザーバ
     */
    fun addObserver(observer: TimelineObserver) {
        synchronized(observers) {
            if (!observers.contains(observer)) {
                putDebugLog("[${observer.timelineId}] Connected TimelineHub.")

                observers += observer

                // キューからのイベント再配信
                synchronized(eventQueues) {
                    if (eventQueues.containsKey(observer.timelineId)) {
                        val queue = eventQueues[observer.timelineId]
                        eventQueues.remove(observer.timelineId)

                        if (queue != null) {
                            putDebugLog("[${observer.timelineId}] ${queue.size} event(s) in queue.")

                            while (!queue.isEmpty()) {
                                observer.onTimelineEvent(queue.poll())
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * タイムラインオブザーバの登録解除
     * @param observer 解除したいオブザーバ
     */
    fun removeObserver(observer: TimelineObserver) {
        synchronized(observers) {
            if (observers.contains(observer)) {
                putDebugLog("[${observer.timelineId}] Disconnected TimelineHub.")

                observers -= observer

                // イベントキューの作成
                synchronized(eventQueues) {
                    eventQueues[observer.timelineId] = LinkedBlockingQueue<TimelineEvent>()
                }
            }
        }
    }

    /**
     * [Status] の受信
     * @param timelineId 配信先識別子
     * @param status 受信したStatus
     * @param passive ストリーミング通信によって受動的に取得したStatusか？
     */
    fun onStatus(timelineId: String, status: Status, passive: Boolean) {
        pushEventQueue(TimelineEvent.Received(timelineId, status), false)
    }

    /**
     * [StatusLoader.requestRestQuery] の処理完了通知の受信
     * @param timelineId 配信先識別子
     * @param taskKey [StatusLoader.requestRestQuery] の戻り値
     */
    fun onRestRequestCompleted(timelineId: String, taskKey: Long) {
        pushEventQueue(TimelineEvent.RestRequestCompleted(timelineId, taskKey))
    }

    /**
     * [StatusLoader.requestRestQuery] の処理中断通知の受信
     * @param timelineId 配信先識別子
     * @param taskKey [StatusLoader.requestRestQuery] の戻り値
     */
    fun onRestRequestCancelled(timelineId: String, taskKey: Long) {
        pushEventQueue(TimelineEvent.RestRequestCancelled(timelineId, taskKey))
    }

    /**
     * タイムラインのクリア
     */
    fun onWipe() {
        pushEventQueue(TimelineEvent.Wipe())
    }

    /**
     * [TimelineEvent] をTL配信キューに登録します。
     *
     * アクティブなTLであれば即時配信され、そうではない場合は次にアクティブになった時点で配信されます。
     * @param event 配信するイベント
     * @param isBroadcast 配信先識別子に関わらず、全てのタイムラインに配信するかどうか
     */
    fun pushEventQueue(event: TimelineEvent, isBroadcast: Boolean = true) {
        synchronized(observers) {
            observers.forEach {
                if (isBroadcast || it.timelineId == event.timelineId) {
                    it.onTimelineEvent(event)
                }
            }
        }
        synchronized(eventQueues) {
            eventQueues.forEach {
                if (isBroadcast || it.key == event.timelineId) {
                    it.value.offer(event)
                }
            }
        }
    }
}

/**
 * [TimelineHub] で発生するイベント
 * @property timelineId 配信先識別子
 */
sealed class TimelineEvent(val timelineId: String) {
    /**
     * [Status] の受信
     * @property status 受信したStatus
     */
    class Received(timelineId: String, val status: Status) : TimelineEvent(timelineId)

    /**
     * [StatusLoader.requestRestQuery] の処理完了
     * @property taskKey [StatusLoader.requestRestQuery] の戻り値
     */
    class RestRequestCompleted(timelineId: String, val taskKey: Long) : TimelineEvent(timelineId)

    /**
     * [StatusLoader.requestRestQuery] の処理中断
     * @property taskKey [StatusLoader.requestRestQuery] の戻り値
     */
    class RestRequestCancelled(timelineId: String, val taskKey: Long) : TimelineEvent(timelineId)

    /**
     * タイムラインのクリア
     */
    class Wipe : TimelineEvent("")

    /**
     * UIの強制更新
     */
    class ForceUpdateUI : TimelineEvent("")
}

/**
 * [TimelineHub] のイベント購読インターフェース
 */
interface TimelineObserver {
    /**
     * 各タイムラインごとに一意の識別子。
     *
     * この値によって、配信キューでの配信先特定を行う。
     */
    val timelineId: String

    /**
     * TL配信キューからのイベント受信時の処理
     * @param event イベント
     */
    fun onTimelineEvent(event: TimelineEvent)
}