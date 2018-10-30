package shibafu.yukari.twitter

import org.junit.Test
import kotlin.test.assertEquals

class TweetValidatorTest {
    @Test
    fun testEmptyStringMeasuredLength() {
        assertEquals(0, TweetValidator().getMeasuredLength("", emptyMap()))
    }

    @Test
    fun testLatin10MeasuredLength() {
        assertEquals(13, TweetValidator().getMeasuredLength("Yuzuki Yukari", emptyMap()))
    }

    @Test
    fun testLatin140MeasuredLength() {
        assertEquals(140,
                TweetValidator().getMeasuredLength("Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim", emptyMap()))
    }

    @Test
    fun testJapanese5MeasuredLength() {
        assertEquals(10, TweetValidator().getMeasuredLength("結月ゆかり", emptyMap()))
    }

    @Test
    fun testJapanese140MeasuredLength() {
        assertEquals(280,
                TweetValidator().getMeasuredLength("メロスは疾風の如く刑場に突入した。間に合った。「待て。その人を殺してはならぬ。メロスが帰って来た。約束のとおり、いま、帰って来た。」と大声で刑場の群衆にむかって叫んだつもりであったが、喉のどがつぶれて嗄しわがれた声が幽かすかに出たばかり、群衆は、ひとりとして彼の到着に気がつかない", emptyMap()))
    }

    @Test
    fun testEmoji3MeasuredLength() {
        assertEquals(6, TweetValidator().getMeasuredLength("🐬🍣🍺", emptyMap()))
    }

    @Test
    fun testHalfWidthJapanese7MeasuredLength() {
        assertEquals(14, TweetValidator().getMeasuredLength("ﾕﾂﾞｷﾕｶﾘ", emptyMap()))
    }
}