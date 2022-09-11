package shibafu.yukari.common

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 要求を一定時間バッファしてから処理する非同期処理ユーティリティ。バッファが有効な間に発行された追加の要求は全て1つにまとめられる。
 */
abstract class BufferedTrigger(private val timeSpanMillis: Long) {
    private val appointed = AtomicBoolean(false)

    /**
     * バッファ時間の終了後に実行される処理を実装する。このメソッドは [trigger] を呼び出したスレッドとは別のスレッドで実行される。
     */
    abstract fun doProcess()

    /**
     * 一定時間後に処理を実行するよう要求する。バッファ時間中に複数回呼び出した場合、それらは1回の呼び出しにまとめられる。
     */
    fun trigger() {
        if (!appointed.compareAndSet(false, true)) {
            return
        }
        scheduler.schedule({
            try {
                doProcess()
            } finally {
                appointed.set(false)
            }
        }, timeSpanMillis, TimeUnit.MILLISECONDS)
    }

    companion object {
        private val scheduler: ScheduledExecutorService by lazy { Executors.newSingleThreadScheduledExecutor() }
    }
}