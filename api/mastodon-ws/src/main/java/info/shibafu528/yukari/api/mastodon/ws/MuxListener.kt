package info.shibafu528.yukari.api.mastodon.ws

import com.sys1yagi.mastodon4j.api.entity.Notification
import com.sys1yagi.mastodon4j.api.entity.Status
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

internal class MuxListener(private val client: StreamClient) : WebSocketListener() {
    override fun onOpen(webSocket: WebSocket, response: Response) {
        System.err.println("MuxListener.onOpen: Connected.")
        client.onOpen(webSocket, response)
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        val event = client.gson.fromJson(text, Event::class.java)
        // TODO: admin専用イベントがいくつかあったような気がする
        when (event.event) {
            "update" -> onUpdate(event) // received new status
            "notification" -> onNotification(event) // received notification
            "delete" -> onDelete(event) // deleted status
            "filters_changed" -> {} // notified filter settings change (but in this case, payload is null)
            "status.update" -> {} // boosted status edit
            "announcement" -> {} // received new announcement from admin
            "announcement.reaction" -> {} // someone reacted to announcement
            "announcement.delete" -> {} // deleted announcement
            else -> {} // unknown event
        }
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        System.err.println("MuxListener.onClosing: Closing...")
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        System.err.println("MuxListener.onClosed: Closed. code = $code, reason = $reason")
        client.subscriptions.forEach {
            it.listener.onClosed()
        }
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        System.err.println("MuxListener.onFailure: Disconnected. code = ${response?.code()}")
        client.onFailure(webSocket, t, response)
        client.subscriptions.forEach {
            it.listener.onFailure(t, response)
        }
    }

    private fun onUpdate(event: Event) {
        val status = client.gson.fromJson(event.payload!!, Status::class.java)
        client.forEachSubscriptionsByStream(event.stream) {
            it.listener.onUpdate(status)
        }
    }

    private fun onNotification(event: Event) {
        val notification = client.gson.fromJson(event.payload!!, Notification::class.java)
        client.forEachSubscriptionsByStream(event.stream) {
            it.listener.onNotification(notification)
        }
    }

    private fun onDelete(event: Event) {
        val id = event.payload?.toLongOrNull() ?: return // TODO: log error
        client.forEachSubscriptionsByStream(event.stream) {
            it.listener.onDelete(id)
        }
    }
}