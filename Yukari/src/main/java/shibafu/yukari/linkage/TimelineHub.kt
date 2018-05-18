package shibafu.yukari.linkage

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager
import android.support.v4.util.LongSparseArray
import android.support.v4.util.LruCache
import info.shibafu528.yukari.exvoice.converter.StatusConverter
import info.shibafu528.yukari.exvoice.pluggaloid.Plugin
import shibafu.yukari.R
import shibafu.yukari.common.HashCache
import shibafu.yukari.common.NotificationType
import shibafu.yukari.database.AutoMuteConfig
import shibafu.yukari.database.MuteConfig
import shibafu.yukari.entity.NotifyHistory
import shibafu.yukari.entity.NotifyKind
import shibafu.yukari.entity.Status
import shibafu.yukari.entity.User
import shibafu.yukari.service.TwitterService
import shibafu.yukari.twitter.entity.TwitterMessage
import shibafu.yukari.twitter.entity.TwitterStatus
import shibafu.yukari.twitter.entity.TwitterUser
import shibafu.yukari.twitter.statusmanager.UserUpdateDelayer
import shibafu.yukari.util.StringUtil
import shibafu.yukari.util.putDebugLog
import java.util.*
import java.util.concurrent.LinkedBlockingQueue
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/**
 * [Status] の配信管理
 */
class TimelineHub(private val service: TwitterService) {
    val hashCache: HashCache = HashCache(service.applicationContext)

    private val context: Context = service.applicationContext
    private val sp: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    private val notifier: StatusNotifier = StatusNotifier(service)
    private val userUpdateDelayer: UserUpdateDelayer = UserUpdateDelayer(service.database)

    // TLオブザーバとキュー (TODO: こいつら同期処理が必要だったはずだけど、うまいことやれないか？)
    private val observers: MutableList<TimelineObserver> = arrayListOf()
    private val eventQueues: MutableMap<String, Queue<TimelineEvent>> = hashMapOf()

    private var autoMuteConfigs: List<AutoMuteConfig> = emptyList()
    private var autoMutePatternCache: LongSparseArray<Pattern> = LongSparseArray()

    /**
     * オートミュート設定のインポート
     * @param autoMuteConfigs 読み込む設定
     */
    fun setAutoMuteConfigs(autoMuteConfigs: List<AutoMuteConfig>) {
        this.autoMuteConfigs = autoMuteConfigs
        autoMutePatternCache.clear()
        autoMuteConfigs.filter { it.match == AutoMuteConfig.MATCH_REGEX }
                       .forEach {
                           try {
                               autoMutePatternCache.put(it.id, Pattern.compile(it.query))
                           } catch (e: PatternSyntaxException) {
                               autoMutePatternCache.put(it.id, null)
                           }
                       }
    }

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
     * @param passive ストリーミング通信によって受動的に取得したStatusか？ (trueの場合、ブロードキャストされる)
     */
    fun onStatus(timelineId: String, status: Status, passive: Boolean) {
        // Twitter DirectMessageは別処理
        if (status is TwitterMessage) {
            onDirectMessage(timelineId, status, passive)
            return
        }

        val plc = getProviderLocalCache(status.representUser.Provider.id)

        // 代表アカウントの上書き
        status.setRepresentIfOwned(service.users)

        // ミュート判定
        val muteFlags = service.suppressor.decision(status)
        if (muteFlags[MuteConfig.MUTE_IMAGE_THUMB]) {
            status.metadata.isCensoredThumbs = true
        }
        val isMuted = muteFlags[MuteConfig.MUTE_TWEET_RTED] ||
                (!status.isRepost && muteFlags[MuteConfig.MUTE_TWEET]) ||
                (status.isRepost && muteFlags[MuteConfig.MUTE_RETWEET])

        // RTレスポンス通知判定
        val standByStatus = plc.repostResponseStandBy[status.user.id]
        if (standByStatus != null) {
            if (NotificationType(sp.getInt("pref_notif_respond", 0)).isEnabled &&
                    !status.isRepost && !status.text.startsWith("@") && status.createdAt > standByStatus.first.createdAt) {
                // RTレスポンスとして処理
                plc.repostResponseStandBy.remove(status.user.id)
                status.metadata.repostRespondTo = standByStatus.first
                // 通知はストリーミング時のみ行う
                if (!passive) {
                    notifier.showNotification(R.integer.notification_respond, status, status.user)
                }
            } else if (standByStatus.second + RESPONSE_STAND_BY_EXPIRES < System.currentTimeMillis()) {
                // 期限切れ
                plc.repostResponseStandBy.remove(status.user.id)
            }
        }

        // 繰り返し文判定
        status.metadata.repeatedSequence = StringUtil.compressText(status.text)

        pushEventQueue(TimelineEvent.Received(timelineId, status, isMuted, passive), passive)

        if (passive) {
            // オートミュート判定
            autoMuteConfigs.forEach { config ->
                var match = false
                when (config.match) {
                    AutoMuteConfig.MATCH_EXACT -> match = status.text == config.query
                    AutoMuteConfig.MATCH_PARTIAL -> match = status.text.contains(config.query)
                    AutoMuteConfig.MATCH_REGEX -> {
                        var pattern = autoMutePatternCache[config.id]
                        if (pattern == null && autoMutePatternCache.indexOfKey(config.id) < 0) {
                            try {
                                pattern = Pattern.compile(config.query)
                                autoMutePatternCache.put(config.id, pattern)
                            } catch (e: PatternSyntaxException) {
                                autoMutePatternCache.put(config.id, null)
                            }
                        }
                        if (pattern != null) {
                            match = pattern.matcher(status.text).find()
                        }
                    }
                }
                if (match) {
                    putDebugLog("[$timelineId] AutoMute! : @${status.user.screenName}")

                    // TODO: サービスに関係なく消えてしまうリスク
                    service.database?.updateRecord(config.getMuteConfig(status.user.screenName, System.currentTimeMillis() + 3600000))
                    service.updateMuteConfig()
                    return@forEach
                }
            }

            // RT通知 & メンション通知判定
            if (status.isRepost && !muteFlags[MuteConfig.MUTE_NOTIF_RT] &&
                    status.originStatus.user.id == status.representUser.NumericId &&
                    status.getStatusRelation(service.users) != Status.RELATION_OWNED) {
                onNotify(NotifyHistory.KIND_RETWEETED, status.user, status)

                // RTレスポンス待機
                plc.repostResponseStandBy.put(status.user.id, Pair(status, System.currentTimeMillis()))
            } else if (!status.isRepost && !muteFlags[MuteConfig.MUTE_NOTIF_MENTION] &&
                    status.providerApiType == status.representUser.Provider.apiType) {
                status.mentions.forEach { mention ->
                    if (mention.id == status.representUser.NumericId) {
                        notifier.showNotification(R.integer.notification_replied, status, status.user)
                    }
                }
            }
        }

        // キャッシュ登録
        plc.receivedStatus.put(status.id, status)

        if (status is TwitterStatus) {
            userUpdateDelayer.enqueue((status.user as TwitterUser).user)
            if (status.isRepost) {
                userUpdateDelayer.enqueue((status.originStatus.user as TwitterUser).user)
            }

            if (passive) {
                // ハッシュタグのキャッシュ
                status.status.hashtagEntities.forEach {
                    hashCache.put("#" + it.text)
                }
            }

            // 引用ツイートのキャッシュ
            if (status.status.quotedStatus != null) {
                val quotedStatus = TwitterStatus(status.status.quotedStatus, status.representUser)
                plc.receivedStatus.put(quotedStatus.id, quotedStatus)
            }
            // TODO: 昔はこのへんで引用ツイートの再帰取得リクエストしてた (StatusManagerの履歴参照)

            // mruby連携
            if (sp.getBoolean("pref_exvoice_experimental_on_appear", false)) {
                val mRuby = service.getmRuby()
                if (mRuby != null) {
                    val message = StatusConverter.toMessage(mRuby, status.status)
                    try {
                        Plugin.call(mRuby, "appear", arrayOf(message))
                    } finally {
                        message.dispose()
                    }
                }
            }
        }
    }

    /**
     * [TwitterMessage] の受信
     *
     * TwitterのDirectMessageは性質の異なる情報のため、別に処理する必要がある。
     * @param timelineId 配信先識別子
     * @param status 受信したStatus
     * @param passive ストリーミング通信によって受動的に取得したStatusか？ (trueの場合、ブロードキャストされる)
     */
    fun onDirectMessage(timelineId: String, status: TwitterMessage, passive: Boolean) {
        // TODO: ベタ移植なので問題があれば作り直す

        userUpdateDelayer.enqueue(status.message.sender)
        pushEventQueue(TimelineEvent.Received(timelineId, status, false, passive), passive)

        if (status.getStatusRelation(service.users) != Status.RELATION_OWNED) {
            notifier.showNotification(R.integer.notification_message, status, status.user)
        }
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
     * 通知イベントログの発生
     * @param kind イベント区分
     * @param eventBy イベントを発生させたユーザ
     * @param status イベントに関連する [Status]
     */
    fun onNotify(@NotifyKind kind: Int, eventBy: User, status: Status) {
        when (kind) {
            NotifyHistory.KIND_FAVED -> notifier.showNotification(R.integer.notification_faved, status, eventBy)
            NotifyHistory.KIND_RETWEETED -> notifier.showNotification(R.integer.notification_retweeted, status, eventBy)
        }

        val notify = NotifyHistory(System.currentTimeMillis(), kind, eventBy, status)
        pushEventQueue(TimelineEvent.Notify(notify))
    }

    /**
     * お気に入り登録イベントの発生
     * @param from お気に入り登録を実行したユーザ
     * @param status 対象の [Status]
     */
    fun onFavorite(from: User, status: Status) {
        pushEventQueue(TimelineEvent.Favorite(from, status))

        // 自分以外によるアクションであれば通知判定を行う
        if (from.id != status.representUser.NumericId) {
            val muteFlags = service.suppressor.decision(status)
            val userMuteFlags = service.suppressor.decisionUser(from)
            if (!(muteFlags[MuteConfig.MUTE_NOTIF_FAV] || userMuteFlags[MuteConfig.MUTE_NOTIF_FAV])) {
                onNotify(NotifyHistory.KIND_FAVED, from, status)
            }
        }

        if (status is TwitterStatus) {
            userUpdateDelayer.enqueue((from as TwitterUser).user)
        }
    }

    /**
     * お気に入り解除イベントの発生
     * @param from お気に入り解除を実行したユーザ
     * @param status 対象の [Status]
     */
    fun onUnfavorite(from: User, status: Status) {
        pushEventQueue(TimelineEvent.Unfavorite(from, status))
    }

    /**
     * 削除イベントの発生
     * @param type 削除対象の型
     * @param id 削除対象のID
     */
    fun onDelete(type: Class<out Status>, id: Long) {
        pushEventQueue(TimelineEvent.Delete(type, id))
    }

    /**
     * タイムラインのクリア
     */
    fun onWipe() {
        pushEventQueue(TimelineEvent.Wipe())
    }

    /**
     * タイムラインの強制リフレッシュ
     */
    fun onForceUpdateUI() {
        pushEventQueue(TimelineEvent.ForceUpdateUI())
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

    companion object {
        private const val RESPONSE_STAND_BY_EXPIRES = 10 * 60 * 1000

        private val providerLocalCaches: LongSparseArray<ProviderLocalCache> = LongSparseArray()

        /**
         * Provider固有キャッシュの取得
         * @param providerId ProviderのID
         */
        fun getProviderLocalCache(providerId: Long): ProviderLocalCache {
            var cache = providerLocalCaches[providerId]
            if (cache == null) {
                cache = ProviderLocalCache()
                providerLocalCaches.put(providerId, cache)
            }
            return cache
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
     * @property muted ミュートフラグ
     * @property passive ストリーミング通信によって受動的に取得したStatusか？
     */
    class Received(timelineId: String, val status: Status, val muted: Boolean, val passive: Boolean) : TimelineEvent(timelineId)

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
     * 通知イベントログの発生
     * @property notify 通知イベントログ
     */
    class Notify(val notify: NotifyHistory) : TimelineEvent("")

    /**
     * お気に入り登録イベントの発生
     * @property from お気に入り登録を実行したユーザ
     * @property status 対象の [Status]
     */
    class Favorite(val from: User, val status: Status) : TimelineEvent("")

    /**
     * お気に入り解除イベントの発生
     * @property from お気に入り解除を実行したユーザ
     * @property status 対象の [Status]
     */
    class Unfavorite(val from: User, val status: Status) : TimelineEvent("")

    /**
     * 削除イベントの発生
     * @property type 削除対象の型
     * @property id 削除対象のID
     */
    class Delete(val type: Class<out Status>, val id: Long) : TimelineEvent("")

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

/**
 * Providerごとに持つキャッシュ情報
 */
class ProviderLocalCache {
    var receivedStatus: LruCache<Long, Status> = LruCache(512)
    var repostResponseStandBy: LongSparseArray<Pair<Status, Long>> = LongSparseArray()
}