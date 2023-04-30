package info.shibafu528.yukari.api.mastodon.ws

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

internal class AutoReconnectWebSocket(private val okHttpClient: OkHttpClient,
                             private val request: Request,
                             private val listener: WebSocketListener) : WebSocketListener(), WebSocket {
    companion object {
        private const val NOT_RETRIED_YET = 0L
        private const val INITIAL_RECONNECT_TIME_TO_SLEEP = 10000L

        // shared retry thread pool
        private val retryExecutor = Executors.newSingleThreadScheduledExecutor()
    }

    private var connection = okHttpClient.newWebSocket(request, this)
    private var previousTimeToSleep = NOT_RETRIED_YET
    private var retryingFuture: ScheduledFuture<*>? = null
    private var isCancelled = false

    override fun onOpen(webSocket: WebSocket, response: Response) {
        listener.onOpen(webSocket, response)

        // reset retry state
        previousTimeToSleep = NOT_RETRIED_YET
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        listener.onMessage(webSocket, text)
    }

    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
        listener.onMessage(webSocket, bytes)
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        listener.onClosing(webSocket, code, reason)
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        listener.onClosed(webSocket, code, reason)
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        listener.onFailure(webSocket, t, response)

        // try reconnect
        if (isCancelled) {
            return
        }
        val timeToSleep = if (previousTimeToSleep == NOT_RETRIED_YET) {
            INITIAL_RECONNECT_TIME_TO_SLEEP
        } else {
            minOf(previousTimeToSleep * 2, 60000)
        }
        previousTimeToSleep = timeToSleep

        retryingFuture = retryExecutor.schedule({
            retryingFuture = null
            connection = okHttpClient.newWebSocket(request, this)
        }, timeToSleep, TimeUnit.MILLISECONDS)
    }

    override fun request(): Request? = connection?.request()

    override fun queueSize(): Long = connection?.queueSize() ?: 0L

    override fun send(text: String): Boolean = connection?.send(text) ?: false

    override fun send(bytes: ByteString): Boolean = connection?.send(bytes) ?: false

    override fun close(code: Int, reason: String?): Boolean {
        if (connection == null || !connection.close(code, reason)) {
            return false
        }
        retryingFuture?.cancel(false)
        retryingFuture = null
        return true
    }

    override fun cancel() {
        isCancelled = true
        connection?.cancel()
        retryingFuture?.cancel(false)
        retryingFuture = null
    }
}