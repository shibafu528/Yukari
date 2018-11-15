package shibafu.yukari.twitter

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import org.eclipse.collections.impl.factory.primitive.LongSets
import org.junit.Test
import shibafu.yukari.linkage.TimelineHub
import shibafu.yukari.twitter.entity.TwitterStatus
import shibafu.yukari.twitter.statusimpl.FakeStatus
import shibafu.yukari.twitter.statusmanager.UserUpdateDelayer
import shibafu.yukari.twitter.streaming.FilterStream
import twitter4j.UserMentionEntity
import twitter4j.auth.AccessToken

/**
 * [FilterStreamListener] の follow 着信のテスト
 */
class FilterStreamListenerFollowTest {

    /**
     * 普通にフォローしてる人のツイート
     */
    @Test
    fun testRegularTweet() {
        val hub = mock<TimelineHub> {}
        val uud = mock<UserUpdateDelayer> {}
        val stream = mock<FilterStream> {
            on { followIds } doReturn LongSets.immutable.of(10, 11, 12)
            on { userRecord } doReturn AuthUserRecord(AccessToken("2257710474-XXXXXXXXXXXXXXX", "XXXXXXXX")).apply {
                ScreenName = "yukari4a"
            }
        }

        val status = object : FakeStatus(0) {
            override fun getUser() = object : FakeUser() {
                override fun getId() = 10L
            }

            override fun getText() = "hoge"
        }

        val listener = FilterStreamListener(hub, uud)
        listener.onStatus(stream, status)

        verify(hub).onStatus(any<String>(), any<TwitterStatus>(), eq(true))
    }

    /**
     * F/F外から失礼するゾ＾〜
     */
    @Test
    fun testNotFollowedTweet() {
        val hub = mock<TimelineHub> {}
        val uud = mock<UserUpdateDelayer> {}
        val stream = mock<FilterStream> {
            on { followIds } doReturn LongSets.immutable.of(10, 11, 12)
            on { userRecord } doReturn AuthUserRecord(AccessToken("2257710474-XXXXXXXXXXXXXXX", "XXXXXXXX")).apply {
                ScreenName = "yukari4a"
            }
        }

        val status = object : FakeStatus(0) {
            override fun getUser() = object : FakeUser() {
                override fun getId() = 20L
            }

            override fun getText() = "hoge"
        }

        val listener = FilterStreamListener(hub, uud)
        listener.onStatus(stream, status)

        verify(hub, never()).onStatus(any<String>(), any<TwitterStatus>(), eq(true))
    }

    /**
     * 自分のみに宛てたリプライ
     */
    @Test
    fun testReplyToMe() {
        val hub = mock<TimelineHub> {}
        val uud = mock<UserUpdateDelayer> {}
        val account = AuthUserRecord(AccessToken("2257710474-XXXXXXXXXXXXXXX", "XXXXXXXX")).apply {
            ScreenName = "yukari4a"
        }
        val stream = mock<FilterStream> {
            on { followIds } doReturn LongSets.immutable.of(10, 11, 12)
            on { userRecord } doReturn account
        }

        val status = object : FakeStatus(0) {
            override fun getUser() = object : FakeUser() {
                override fun getId() = 10L
            }

            override fun getText() = "@${account.ScreenName} hoge"

            override fun getUserMentionEntities() = arrayOf(
                    UserMentionEntityMock(account.NumericId)
            )
        }

        val listener = FilterStreamListener(hub, uud)
        listener.onStatus(stream, status)

        verify(hub).onStatus(any<String>(), any<TwitterStatus>(), eq(true))
    }

    /**
     * 発言者自身へのセルフリプライ
     */
    @Test
    fun testReplyToYourself() {
        val hub = mock<TimelineHub> {}
        val uud = mock<UserUpdateDelayer> {}
        val account = AuthUserRecord(AccessToken("2257710474-XXXXXXXXXXXXXXX", "XXXXXXXX")).apply {
            ScreenName = "yukari4a"
        }
        val stream = mock<FilterStream> {
            on { followIds } doReturn LongSets.immutable.of(10, 11, 12)
            on { userRecord } doReturn account
        }

        val status = object : FakeStatus(0) {
            override fun getUser() = object : FakeUser() {
                override fun getId() = 10L
                override fun getScreenName() = "fakeuser"
            }

            override fun getText() = "@${user.screenName} hoge"

            override fun getUserMentionEntities() = arrayOf(
                    UserMentionEntityMock(user.id)
            )
        }

        val listener = FilterStreamListener(hub, uud)
        listener.onStatus(stream, status)

        verify(hub).onStatus(any<String>(), any<TwitterStatus>(), eq(true))
    }

    /**
     * 自分と発言者自身へのリプライ
     */
    @Test
    fun testReplyToMeAndYourself() {
        val hub = mock<TimelineHub> {}
        val uud = mock<UserUpdateDelayer> {}
        val account = AuthUserRecord(AccessToken("2257710474-XXXXXXXXXXXXXXX", "XXXXXXXX")).apply {
            ScreenName = "yukari4a"
        }
        val stream = mock<FilterStream> {
            on { followIds } doReturn LongSets.immutable.of(10, 11, 12)
            on { userRecord } doReturn account
        }

        val status = object : FakeStatus(0) {
            override fun getUser() = object : FakeUser() {
                override fun getId() = 10L
                override fun getScreenName() = "fakeuser"
            }

            override fun getText() = "@{account.ScreenName} @${user.screenName} hoge"

            override fun getUserMentionEntities() = arrayOf(
                    UserMentionEntityMock(account.NumericId),
                    UserMentionEntityMock(user.id)
            )
        }

        val listener = FilterStreamListener(hub, uud)
        listener.onStatus(stream, status)

        verify(hub).onStatus(any<String>(), any<TwitterStatus>(), eq(true))
    }

    /**
     * 赤の他人へのリプライ
     */
    @Test
    fun testReplyNotToMe() {
        val hub = mock<TimelineHub> {}
        val uud = mock<UserUpdateDelayer> {}
        val account = AuthUserRecord(AccessToken("2257710474-XXXXXXXXXXXXXXX", "XXXXXXXX")).apply {
            ScreenName = "yukari4a"
        }
        val stream = mock<FilterStream> {
            on { followIds } doReturn LongSets.immutable.of(10, 11, 12)
            on { userRecord } doReturn account
        }

        val status = object : FakeStatus(0) {
            override fun getUser() = object : FakeUser() {
                override fun getId() = 10L
            }

            override fun getText() = "@nottarget hoge"

            override fun getUserMentionEntities() = arrayOf(
                    UserMentionEntityMock(20)
            )
        }

        val listener = FilterStreamListener(hub, uud)
        listener.onStatus(stream, status)

        verify(hub, never()).onStatus(any<String>(), any<TwitterStatus>(), eq(true))
    }

    /**
     * 発言者自身と赤の他人へのリプライ
     */
    @Test
    fun testReplyNotToMe2() {
        val hub = mock<TimelineHub> {}
        val uud = mock<UserUpdateDelayer> {}
        val account = AuthUserRecord(AccessToken("2257710474-XXXXXXXXXXXXXXX", "XXXXXXXX")).apply {
            ScreenName = "yukari4a"
        }
        val stream = mock<FilterStream> {
            on { followIds } doReturn LongSets.immutable.of(10, 11, 12)
            on { userRecord } doReturn account
        }

        val status = object : FakeStatus(0) {
            override fun getUser() = object : FakeUser() {
                override fun getId() = 10L
                override fun getScreenName() = "fakeuser"
            }

            override fun getText() = "@${user.screenName} @nottarget hoge"

            override fun getUserMentionEntities() = arrayOf(
                    UserMentionEntityMock(user.id),
                    UserMentionEntityMock(20)
            )
        }

        val listener = FilterStreamListener(hub, uud)
        listener.onStatus(stream, status)

        verify(hub, never()).onStatus(any<String>(), any<TwitterStatus>(), eq(true))
    }

    /**
     * 別のフォロイーに宛てたリプライ
     */
    @Test
    fun testReplyToFriend() {
        val hub = mock<TimelineHub> {}
        val uud = mock<UserUpdateDelayer> {}
        val account = AuthUserRecord(AccessToken("2257710474-XXXXXXXXXXXXXXX", "XXXXXXXX")).apply {
            ScreenName = "yukari4a"
        }
        val stream = mock<FilterStream> {
            on { followIds } doReturn LongSets.immutable.of(10, 11, 12)
            on { userRecord } doReturn account
        }

        val status = object : FakeStatus(0) {
            override fun getUser() = object : FakeUser() {
                override fun getId() = 10L
            }

            override fun getText() = "@fakeuser2 hoge"

            override fun getUserMentionEntities() = arrayOf(
                    UserMentionEntityMock(11)
            )
        }

        val listener = FilterStreamListener(hub, uud)
        listener.onStatus(stream, status)

        verify(hub).onStatus(any<String>(), any<TwitterStatus>(), eq(true))
    }

    /**
     * 別のフォロイーと発言者自身に宛てたリプライ
     */
    @Test
    fun testReplyToFriendAndYourself() {
        val hub = mock<TimelineHub> {}
        val uud = mock<UserUpdateDelayer> {}
        val account = AuthUserRecord(AccessToken("2257710474-XXXXXXXXXXXXXXX", "XXXXXXXX")).apply {
            ScreenName = "yukari4a"
        }
        val stream = mock<FilterStream> {
            on { followIds } doReturn LongSets.immutable.of(10, 11, 12)
            on { userRecord } doReturn account
        }

        val status = object : FakeStatus(0) {
            override fun getUser() = object : FakeUser() {
                override fun getId() = 10L
            }

            override fun getText() = "@fakeuser @fakeuser2 hoge"

            override fun getUserMentionEntities() = arrayOf(
                    UserMentionEntityMock(user.id),
                    UserMentionEntityMock(11)
            )
        }

        val listener = FilterStreamListener(hub, uud)
        listener.onStatus(stream, status)

        verify(hub).onStatus(any<String>(), any<TwitterStatus>(), eq(true))
    }

    /**
     * 自分と別のフォロイーに宛てたリプライ
     */
    @Test
    fun testReplyToMeAndFriend() {
        val hub = mock<TimelineHub> {}
        val uud = mock<UserUpdateDelayer> {}
        val account = AuthUserRecord(AccessToken("2257710474-XXXXXXXXXXXXXXX", "XXXXXXXX")).apply {
            ScreenName = "yukari4a"
        }
        val stream = mock<FilterStream> {
            on { followIds } doReturn LongSets.immutable.of(10, 11, 12)
            on { userRecord } doReturn account
        }

        val status = object : FakeStatus(0) {
            override fun getUser() = object : FakeUser() {
                override fun getId() = 10L
            }

            override fun getText() = "@${account.ScreenName} @fakeuser2 hoge"

            override fun getUserMentionEntities() = arrayOf(
                    UserMentionEntityMock(account.NumericId),
                    UserMentionEntityMock(11)
            )
        }

        val listener = FilterStreamListener(hub, uud)
        listener.onStatus(stream, status)

        verify(hub).onStatus(any<String>(), any<TwitterStatus>(), eq(true))
    }
}

private class UserMentionEntityMock(private val id: Long,
                                    private val name: String? = "",
                                    private val screenName: String? = "",
                                    private val text: String? = "") : UserMentionEntity {
    override fun getId() = id
    override fun getName() = name
    override fun getScreenName() = screenName
    override fun getText() = text

    override fun getStart() = 0
    override fun getEnd() = 0
}