@file:Suppress("NonAsciiCharacters", "TestFunctionName")

package shibafu.yukari.common

import org.amshove.kluent.shouldBeFalse
import org.amshove.kluent.shouldBeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import shibafu.yukari.database.MuteConfig
import shibafu.yukari.database.MuteMatch
import shibafu.yukari.twitter.AuthUserRecord
import shibafu.yukari.twitter.TwitterUtil
import shibafu.yukari.twitter.entity.TwitterStatus
import twitter4j.TwitterObjectFactory
import twitter4j.auth.AccessToken

@RunWith(RobolectricTestRunner::class)
class SuppressorTest {
    private lateinit var twitterUserRecord: AuthUserRecord

    @Before
    fun setUp() {
        twitterUserRecord = AuthUserRecord(AccessToken("26197127-XXXXXXXXXXXXXXX", "XXXXXXXXXXXXXXXX")).apply {
            ScreenName = "shibafu528"
            Url = TwitterUtil.getProfileUrl(ScreenName)
            IdenticalUrl = TwitterUtil.getUrlFromUserId(NumericId)
        }
    }

    @Test
    fun ブロック済ユーザのツイートはミュートされる() {
        val s = Suppressor().apply {
            addBlockedIDs(longArrayOf(26197127))
            configs = emptyList()
        }

        val st = TwitterObjectFactory.createStatus(ClassLoader.getSystemResource("shibafu/yukari/twitter/1152983728563507201.json").readText())
        val result = s.decision(TwitterStatus(st, twitterUserRecord))
        
        result[MuteConfig.MUTE_TWEET_RTED].shouldBeTrue()
    }

    @Test
    fun ブロック済ユーザがリツイートをした場合はミュートされる() {
        val s = Suppressor().apply {
            addBlockedIDs(longArrayOf(26197127))
            configs = emptyList()
        }

        val st = TwitterObjectFactory.createStatus(ClassLoader.getSystemResource("shibafu/yukari/twitter/1149331550514761728.json").readText())
        val result = s.decision(TwitterStatus(st, twitterUserRecord))

        result[MuteConfig.MUTE_RETWEET].shouldBeTrue()
    }

    @Test
    fun TwitterWebでミュート済ユーザのツイートはミュートされる() {
        val s = Suppressor().apply {
            addMutedIDs(longArrayOf(26197127))
            configs = emptyList()
        }

        val st = TwitterObjectFactory.createStatus(ClassLoader.getSystemResource("shibafu/yukari/twitter/1152983728563507201.json").readText())
        val result = s.decision(TwitterStatus(st, twitterUserRecord))

        result[MuteConfig.MUTE_TWEET_RTED].shouldBeTrue()
    }

    @Test
    fun TwitterWebでミュート済ユーザがリツイートをした場合はミュートされる() {
        val s = Suppressor().apply {
            addMutedIDs(longArrayOf(26197127))
            configs = emptyList()
        }

        val st = TwitterObjectFactory.createStatus(ClassLoader.getSystemResource("shibafu/yukari/twitter/1149331550514761728.json").readText())
        val result = s.decision(TwitterStatus(st, twitterUserRecord))

        result[MuteConfig.MUTE_RETWEET].shouldBeTrue()
    }

    @Test
    fun TwitterWebでリツイート非表示にしたユーザがリツイートをした場合はミュートされる() {
        val s = Suppressor().apply {
            addNoRetweetIDs(longArrayOf(26197127))
            configs = emptyList()
        }

        val st = TwitterObjectFactory.createStatus(ClassLoader.getSystemResource("shibafu/yukari/twitter/1149331550514761728.json").readText())
        val result = s.decision(TwitterStatus(st, twitterUserRecord))

        result[MuteConfig.MUTE_RETWEET].shouldBeTrue()
    }

    @Test
    fun TwitterWebでリツイート非表示にしたユーザのツイートはミュートされない() {
        val s = Suppressor().apply {
            addNoRetweetIDs(longArrayOf(26197127))
            configs = emptyList()
        }

        val st = TwitterObjectFactory.createStatus(ClassLoader.getSystemResource("shibafu/yukari/twitter/1152983728563507201.json").readText())
        val result = s.decision(TwitterStatus(st, twitterUserRecord))

        result[MuteConfig.MUTE_TWEET].shouldBeFalse()
        result[MuteConfig.MUTE_TWEET_RTED].shouldBeFalse()
    }

    @Test
    fun 本文完全一致でヒットした場合はミュートされる() {
        val s = Suppressor().apply {
            configs = listOf(MuteConfig(MuteConfig.SCOPE_TEXT, MuteMatch.MATCH_EXACT, MuteConfig.MUTE_TWEET, "そろそろおふとんに入ろう"))
        }

        val st = TwitterObjectFactory.createStatus(ClassLoader.getSystemResource("shibafu/yukari/twitter/1152983728563507201.json").readText())
        val result = s.decision(TwitterStatus(st, twitterUserRecord))

        result[MuteConfig.MUTE_TWEET].shouldBeTrue()
    }

    @Test
    fun 本文完全一致でヒットしなかった場合はミュートされない() {
        val s = Suppressor().apply {
            configs = listOf(MuteConfig(MuteConfig.SCOPE_TEXT, MuteMatch.MATCH_EXACT, MuteConfig.MUTE_TWEET, "そろそろおふとんに入ろう！"))
        }

        val st = TwitterObjectFactory.createStatus(ClassLoader.getSystemResource("shibafu/yukari/twitter/1152983728563507201.json").readText())
        val result = s.decision(TwitterStatus(st, twitterUserRecord))

        result[MuteConfig.MUTE_TWEET].shouldBeFalse()
    }

    @Test
    fun 本文部分一致でヒットした場合はミュートされる() {
        val s = Suppressor().apply {
            configs = listOf(MuteConfig(MuteConfig.SCOPE_TEXT, MuteMatch.MATCH_PARTIAL, MuteConfig.MUTE_TWEET, "おふとん"))
        }

        val st = TwitterObjectFactory.createStatus(ClassLoader.getSystemResource("shibafu/yukari/twitter/1152983728563507201.json").readText())
        val result = s.decision(TwitterStatus(st, twitterUserRecord))

        result[MuteConfig.MUTE_TWEET].shouldBeTrue()
    }

    @Test
    fun 本文正規表現検索でヒットした場合はミュートされる() {
        val s = Suppressor().apply {
            configs = listOf(MuteConfig(MuteConfig.SCOPE_TEXT, MuteMatch.MATCH_REGEX, MuteConfig.MUTE_TWEET, "^(そろ){2}"))
        }

        val st = TwitterObjectFactory.createStatus(ClassLoader.getSystemResource("shibafu/yukari/twitter/1152983728563507201.json").readText())
        val result = s.decision(TwitterStatus(st, twitterUserRecord))

        result[MuteConfig.MUTE_TWEET].shouldBeTrue()
    }

    @Test
    fun ユーザ名完全一致でヒットした場合はミュートされる() {
        val s = Suppressor().apply {
            configs = listOf(MuteConfig(MuteConfig.SCOPE_USER_NAME, MuteMatch.MATCH_EXACT, MuteConfig.MUTE_TWEET, "芝生"))
        }

        val st = TwitterObjectFactory.createStatus(ClassLoader.getSystemResource("shibafu/yukari/twitter/1152983728563507201.json").readText())
        val result = s.decision(TwitterStatus(st, twitterUserRecord))

        result[MuteConfig.MUTE_TWEET].shouldBeTrue()
    }

    @Test
    fun ScreenName完全一致でヒットした場合はミュートされる() {
        val s = Suppressor().apply {
            configs = listOf(MuteConfig(MuteConfig.SCOPE_USER_SN, MuteMatch.MATCH_EXACT, MuteConfig.MUTE_TWEET, "shibafu528"))
        }

        val st = TwitterObjectFactory.createStatus(ClassLoader.getSystemResource("shibafu/yukari/twitter/1152983728563507201.json").readText())
        val result = s.decision(TwitterStatus(st, twitterUserRecord))

        result[MuteConfig.MUTE_TWEET].shouldBeTrue()
    }

    @Test
    fun ID完全一致でヒットした場合はミュートされる() {
        val s = Suppressor().apply {
            configs = listOf(MuteConfig(MuteConfig.SCOPE_USER_ID, MuteMatch.MATCH_EXACT, MuteConfig.MUTE_TWEET, "26197127"))
        }

        val st = TwitterObjectFactory.createStatus(ClassLoader.getSystemResource("shibafu/yukari/twitter/1152983728563507201.json").readText())
        val result = s.decision(TwitterStatus(st, twitterUserRecord))

        result[MuteConfig.MUTE_TWEET].shouldBeTrue()
    }

    @Test
    fun via完全一致でヒットした場合はミュートされる() {
        val s = Suppressor().apply {
            configs = listOf(MuteConfig(MuteConfig.SCOPE_VIA, MuteMatch.MATCH_EXACT, MuteConfig.MUTE_TWEET, "StarryEyes (g)"))
        }

        val st = TwitterObjectFactory.createStatus(ClassLoader.getSystemResource("shibafu/yukari/twitter/1152983728563507201.json").readText())
        val result = s.decision(TwitterStatus(st, twitterUserRecord))

        result[MuteConfig.MUTE_TWEET].shouldBeTrue()
    }

    @Test
    fun 正規表現検索でパターンが不正な場合はミュートされない() {
        val s = Suppressor().apply {
            configs = listOf(MuteConfig(MuteConfig.SCOPE_TEXT, MuteMatch.MATCH_REGEX, MuteConfig.MUTE_TWEET, "{"))
        }

        val st = TwitterObjectFactory.createStatus(ClassLoader.getSystemResource("shibafu/yukari/twitter/1152983728563507201.json").readText())
        val result = s.decision(TwitterStatus(st, twitterUserRecord))

        result[MuteConfig.MUTE_TWEET].shouldBeFalse()
    }
}