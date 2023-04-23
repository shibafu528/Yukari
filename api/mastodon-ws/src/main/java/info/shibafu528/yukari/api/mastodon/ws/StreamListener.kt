package info.shibafu528.yukari.api.mastodon.ws

import com.sys1yagi.mastodon4j.api.entity.Notification
import com.sys1yagi.mastodon4j.api.entity.Status
import okhttp3.Response

/**
 * サーバから受け取った各種イベントを購読するためのインターフェースです。
 */
interface StreamListener {
    fun onUpdate(status: Status)
    fun onNotification(notification: Notification)
    fun onDelete(id: Long)

    /**
     * 接続が確立された時に呼び出されます。
     */
    fun onOpen()

    /**
     * 通信が正常に終了し、切断された時に呼び出されます。[onFailure] とは排他的な関係にあり、切断時にはどちらか一方が呼び出されます。
     */
    fun onClosed()

    /**
     * エラーにより通信が異常終了した時に呼び出されます。[onClosed] とは排他的な関係にあり、切断時にはどちらか一方が呼び出されます。
     */
    fun onFailure(t: Throwable, response: Response?)
}