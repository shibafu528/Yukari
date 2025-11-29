package info.shibafu528.yukari.api.mastodon.ws

import com.sys1yagi.mastodon4j.api.entity.Notification
import com.sys1yagi.mastodon4j.api.entity.Status
import okhttp3.Response
import org.junit.Test
import kotlin.test.*

class SubscriptionTest {
    class StubStreamListener : StreamListener {
        override fun onUpdate(status: Status) {
        }

        override fun onNotification(notification: Notification) {
        }

        override fun onDelete(id: Long) {
        }

        override fun onOpen() {
        }

        override fun onClosed() {
        }

        override fun onFailure(t: Throwable, response: Response?) {
        }
    }

    @Test
    fun toSubscribeUserStreamMessage() {
        assertEquals(Subscription("user", StubStreamListener()).toMessage("subscribe"), "{\"stream\":\"user\",\"type\":\"subscribe\"}")
    }

    @Test
    fun toSubscribeListMessage() {
        assertEquals(Subscription("list", StubStreamListener(), list = "42").toMessage("subscribe"), "{\"stream\":\"list\",\"list\":\"42\",\"type\":\"subscribe\"}")
    }

    @Test
    fun toSubscribeTagMessage() {
        assertEquals(Subscription("hashtag", StubStreamListener(), tag = "結月ゆかり").toMessage("subscribe"), "{\"stream\":\"hashtag\",\"tag\":\"結月ゆかり\",\"type\":\"subscribe\"}")
    }
}