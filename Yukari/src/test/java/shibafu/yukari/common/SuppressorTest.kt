package shibafu.yukari.common

import org.amshove.kluent.shouldBeFalse
import org.amshove.kluent.shouldBeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import shibafu.yukari.database.MuteConfig
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
    fun muteAnyTweetByBlockedUser() {
        val s = Suppressor().apply {
            addBlockedIDs(longArrayOf(26197127))
            configs = emptyList()
        }

        val st = TwitterObjectFactory.createStatus(ClassLoader.getSystemResource("shibafu/yukari/twitter/1152983728563507201.json").readText())
        val result = s.decision(TwitterStatus(st, twitterUserRecord))
        
        result[MuteConfig.MUTE_TWEET_RTED].shouldBeTrue()
    }

    @Test
    fun muteAnyTweetByMutedUser() {
        val s = Suppressor().apply {
            addMutedIDs(longArrayOf(26197127))
            configs = emptyList()
        }

        val st = TwitterObjectFactory.createStatus(ClassLoader.getSystemResource("shibafu/yukari/twitter/1152983728563507201.json").readText())
        val result = s.decision(TwitterStatus(st, twitterUserRecord))

        result[MuteConfig.MUTE_TWEET_RTED].shouldBeTrue()
    }

    @Test
    fun muteRetweetByNoRetweetUser() {
        val s = Suppressor().apply {
            addNoRetweetIDs(longArrayOf(26197127))
            configs = emptyList()
        }

        val st = TwitterObjectFactory.createStatus(ClassLoader.getSystemResource("shibafu/yukari/twitter/1149331550514761728.json").readText())
        val result = s.decision(TwitterStatus(st, twitterUserRecord))

        result[MuteConfig.MUTE_RETWEET].shouldBeTrue()
    }

    @Test
    fun notMuteTweetByNoRetweetUser() {
        val s = Suppressor().apply {
            addNoRetweetIDs(longArrayOf(26197127))
            configs = emptyList()
        }

        val st = TwitterObjectFactory.createStatus(ClassLoader.getSystemResource("shibafu/yukari/twitter/1152983728563507201.json").readText())
        val result = s.decision(TwitterStatus(st, twitterUserRecord))

        result[MuteConfig.MUTE_TWEET].shouldBeFalse()
        result[MuteConfig.MUTE_TWEET_RTED].shouldBeFalse()
    }
}