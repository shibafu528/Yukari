package shibafu.yukari.mastodon

import org.junit.Test
import kotlin.test.assertEquals

class MastodonUtilTest {

    @Test
    fun compressAcct_NotCompress1() {
        val result = MastodonUtil.compressAcct("user@e.com")
        assertEquals("user@e.com", result)
    }

    @Test
    fun compressAcct_NotCompress2() {
        // twitterとか、そもそもacctではないやつ
        val result = MastodonUtil.compressAcct("user")
        assertEquals("user", result)
    }

    @Test
    fun compressAcct_1() {
        val result = MastodonUtil.compressAcct("user@example.com")
        assertEquals("user@e5e.com", result)
    }

    @Test
    fun compressAcct_2() {
        val result = MastodonUtil.compressAcct("user@example.comm")
        assertEquals("user@e5e.c2m", result)
    }

    @Test
    fun compressAcct_3() {
        val result = MastodonUtil.compressAcct("user@social.mikutter.hachune.net")
        assertEquals("user@s4l.m6r.h5e.net", result)
    }

    @Test
    fun compressAcct_4() {
        val result = MastodonUtil.compressAcct("user@mstdn.nere9.help")
        assertEquals("user@m3n.n2e9.h2p", result)
    }

    @Test
    fun compressAcct_5() {
        val result = MastodonUtil.compressAcct("user@best-friends.chat")
        assertEquals("user@b2t-f5s.c2t", result)
    }
}