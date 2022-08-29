package shibafu.yukari.twitter.streaming

import android.app.NotificationManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import org.eclipse.collections.api.set.primitive.MutableLongSet
import org.eclipse.collections.impl.factory.primitive.LongSets
import shibafu.yukari.R
import shibafu.yukari.database.AuthUserRecord
import shibafu.yukari.twitter.TwitterUtil
import shibafu.yukari.util.CompatUtil
import twitter4j.RateLimitStatus
import twitter4j.TwitterException
import java.util.concurrent.atomic.AtomicInteger

/**
 * こんなものストリーミングじゃない！ただの鬼ポーリングよ！
 */
class AutoReloadStream(private val context: Context,
                       private val user: AuthUserRecord) : Stream(context, user) {
    private var workerThread: Thread? = null
    private val receivedIds: MutableLongSet = LongSets.mutable.empty()
    private val notificationId = notificationIdGenerator.getAndIncrement()

    override fun getStreamType(): String = "AutoReload"

    override fun start() {
        Log.d(LOG_TAG, "Start AutoReload user: @${user.ScreenName}")
        val worker = Thread(AutoReloadWorker())
        worker.start()
        workerThread = worker
    }

    override fun stop() {
        Log.d(LOG_TAG, "Stop AutoReload user: @${user.ScreenName}")
        workerThread?.interrupt()
    }

    private inner class AutoReloadWorker : Runnable {
        private val handler: Handler = Handler(Looper.getMainLooper())

        override fun run() {
            try {
                var inFirstLoop = true
                var waitSecondsHome = 60
                var waitSecondsMentions = 60
                val twitter = TwitterUtil.getTwitterFactory(context).getInstance(user.twitterAccessToken)

                while (true) {
                    // home, mentionが対象
                    try {
                        val homeResponse = twitter.homeTimeline
                        val mentionsResponse = twitter.mentionsTimeline

                        homeResponse.reversed().forEach {
                            if (!receivedIds.contains(it.id)) {
                                listener?.onStatus(this@AutoReloadStream, it)
                                receivedIds.add(it.id)
                            }
                        }
                        mentionsResponse.reversed().forEach {
                            if (inFirstLoop) {
                                // 初回ロードでは通知が爆発するので受信済フラグだけ立てて全部捨てる どうせTL側でロードしているので
                                receivedIds.add(it.id)
                            } else if (!receivedIds.contains(it.id)) {
                                listener?.onStatus(this@AutoReloadStream, it)
                                receivedIds.add(it.id)
                            }
                        }

                        Log.d(LOG_TAG, "RateLimitStatus Home : remaining = ${homeResponse.rateLimitStatus.remaining}, " +
                                "limit = ${homeResponse.rateLimitStatus.limit}, " +
                                "resetTimeInSeconds = ${homeResponse.rateLimitStatus.resetTimeInSeconds}, " +
                                "secondsUntilReset = ${homeResponse.rateLimitStatus.secondsUntilReset}")
                        Log.d(LOG_TAG, "RateLimitStatus Mentions : remaining = ${mentionsResponse.rateLimitStatus.remaining}, " +
                                "limit = ${mentionsResponse.rateLimitStatus.limit}, " +
                                "resetTimeInSeconds = ${mentionsResponse.rateLimitStatus.resetTimeInSeconds}, " +
                                "secondsUntilReset = ${mentionsResponse.rateLimitStatus.secondsUntilReset}")

                        // 次の実行時間の決定、より長いほうを採用して待機する
                        waitSecondsHome = calculateWaitSeconds(homeResponse.rateLimitStatus, waitSecondsHome)
                        waitSecondsMentions = calculateWaitSeconds(mentionsResponse.rateLimitStatus, waitSecondsMentions)
                        val waitSeconds = maxOf(waitSecondsHome, waitSecondsMentions, 5) // 最低5秒は待機する

                        if (inFirstLoop) {
                            inFirstLoop = false
                        }

                        Log.d(LOG_TAG, "Next after $waitSeconds secs. user: @${user.ScreenName}")
                        Thread.sleep(waitSeconds * 1000L)
                    } catch (e: TwitterException) {
                        e.printStackTrace()

                        when (e.statusCode) {
                            429 -> {
                                notifyError(String.format("レートリミット超過\n次回リセット: %d分%d秒後",
                                        e.rateLimitStatus.secondsUntilReset / 60,
                                        e.rateLimitStatus.secondsUntilReset % 60))

                                val waitSeconds = maxOf(e.rateLimitStatus.secondsUntilReset, 60)
                                Log.d(LOG_TAG, "Next after $waitSeconds secs. user: @${user.ScreenName}")
                                Thread.sleep(waitSeconds * 1000L)
                            }
                            else -> {
                                notifyError(String.format("%d:%d 通信エラー\n%s",
                                        e.statusCode,
                                        e.errorCode,
                                        e.errorMessage ?: e.cause?.toString() ?: e.toString()))

                                Log.d(LOG_TAG, "Next after 60 secs. user: @${user.ScreenName}")
                                Thread.sleep(60000L)
                            }
                        }
                    } catch (e: InterruptedException) {
                        throw e
                    } catch (e: Exception) {
                        e.printStackTrace()

                        notifyError("エラーが発生しました\n${e.javaClass.simpleName}")

                        Log.d(LOG_TAG, "Next after 60 secs. user: @${user.ScreenName}")
                        Thread.sleep(60000L)
                    }
                }
            } catch (e: InterruptedException) {
                Log.d(LOG_TAG, "Interrupt in auto reload worker thread. user: @${user.ScreenName}")
            }
        }

        private fun calculateWaitSeconds(rateLimitStatus: RateLimitStatus, lastWaitSeconds: Int): Int {
            if (rateLimitStatus.remaining > 0) {
                return rateLimitStatus.secondsUntilReset / rateLimitStatus.remaining
            } else {
                return lastWaitSeconds
            }
        }

        private fun notifyError(text: String) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notification = NotificationCompat.Builder(context, context.getString(R.string.notification_channel_id_error))
                    .setSmallIcon(android.R.drawable.stat_notify_error)
                    .setTicker("自動リロードエラー @${user.ScreenName}")
                    .setContentTitle("自動リロードエラー @${user.ScreenName}")
                    .setContentText(text)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setContentIntent(CompatUtil.getEmptyPendingIntent(context))
                    .setWhen(System.currentTimeMillis())
                    .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                    .build()
            nm.notify("shibafu.yukari.twitter.streaming.AUTO_RELOAD_ERROR", notificationId, notification)
        }
    }

    companion object {
        private const val LOG_TAG = "AutoReloadStream"
        private val notificationIdGenerator = AtomicInteger()
    }
}