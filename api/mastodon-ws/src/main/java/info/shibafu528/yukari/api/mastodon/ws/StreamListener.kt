package info.shibafu528.yukari.api.mastodon.ws

import com.sys1yagi.mastodon4j.api.entity.Notification
import com.sys1yagi.mastodon4j.api.entity.Status

/**
 * サーバから受け取った各種イベントを購読するためのインターフェースです。
 */
interface StreamListener {
    fun onUpdate(status: Status)
    fun onNotification(notification: Notification)
    fun onDelete(id: Long)
    fun onClosed()
}