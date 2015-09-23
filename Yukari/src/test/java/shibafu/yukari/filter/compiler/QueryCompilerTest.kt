package shibafu.yukari.filter.compiler

import org.junit.Test
import shibafu.yukari.filter.sexp.SNode
import shibafu.yukari.filter.source.All
import shibafu.yukari.filter.source.FilterSource
import shibafu.yukari.filter.source.Home
import shibafu.yukari.twitter.AuthUserRecord
import shibafu.yukari.twitter.statusimpl.FakeStatus
import twitter4j.auth.AccessToken
import java.lang.reflect.InvocationTargetException
import kotlin.test.assertEquals

/**
 * Created by shibafu on 15/07/20.
 */
public class QueryCompilerTest {

    @Suppress("UNCHECKED_CAST")
    private fun parseSource(fromQuery: String, userRecords: List<AuthUserRecord> = emptyList()): List<FilterSource> {
        val method = QueryCompiler.Companion::class.java.getDeclaredMethod("parseSource", String::class.java, List::class.java)
        method.isAccessible = true
        return method.invoke(QueryCompiler.Companion, fromQuery, userRecords) as List<FilterSource>
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseExpression(sexp: String, userRecords: List<AuthUserRecord>): SNode {
        val method = QueryCompiler.Companion::class.java.getDeclaredMethod("parseExpression", String::class.java, List::class.java)
        method.isAccessible = true
        return method.invoke(QueryCompiler.Companion, sexp, userRecords) as SNode
    }

    @Test fun emptySourceTest() {
        val source = parseSource("from")
        assertEquals(1, source.size())
        assertEquals(All::class.java, source.first().javaClass)
    }

    @Test fun allSourceTest() {
        val source = parseSource("from all")
        assertEquals(1, source.size())
        assertEquals(All::class.java, source.first().javaClass)
    }

    @Test fun doubleAllSourceTest() {
        val source = parseSource("from all, all")
        assertEquals(2, source.size())
        assertEquals(All::class.java, source.first().javaClass)
        assertEquals(All::class.java, source.drop(1).first().javaClass)
    }

    @Test fun localSourceTest() {
        val source = parseSource("from local")
        assertEquals(1, source.size())
        assertEquals(All::class.java, source.first().javaClass)
    }

    @Test fun asteriskSourceTest() {
        val source = parseSource("from *")
        assertEquals(1, source.size())
        assertEquals(All::class.java, source.first().javaClass)
    }

    @Test(expected = FilterCompilerException::class) fun illegalSourceTest() {
        try {
            parseSource("from manaita")
        } catch (e: InvocationTargetException) {
            throw e.getCause()!!
        }
    }

    @Test fun homeSourceTest() {
        val sampleUser = AuthUserRecord(AccessToken("2257710474-XXXXXXXXXXXXXXX", "XXXXXXXXXXXXXXXX"))
        sampleUser.ScreenName = "yukari4a"

        val source = parseSource("from home:\"yukari4a\"", listOf(sampleUser))
        assertEquals(1, source.size())
        assertEquals(Home::class.java, source.first().javaClass)
        assertEquals(sampleUser, source.first().sourceAccount)
    }

    @Test fun multiHomeSourceTest1() {
        val sampleUser = AuthUserRecord(AccessToken("2257710474-XXXXXXXXXXXXXXX", "XXXXXXXXXXXXXXXX"))
        sampleUser.ScreenName = "yukari4a"
        val sampleUser2 = AuthUserRecord(AccessToken("26197127-XXXXXXXXXXXXXXX", "XXXXXXXXXXXXXXXX"))
        sampleUser2.ScreenName = "shibafu528"

        val source = parseSource("from home:\"yukari4a\", \"shibafu528\"", listOf(sampleUser, sampleUser2))
        assertEquals(2, source.size())
        assertEquals(Home::class.java, source.first().javaClass)
        assertEquals(Home::class.java, source.drop(1).first().javaClass)
        assertEquals(sampleUser, source.first().sourceAccount)
        assertEquals(sampleUser2, source.drop(1).first().sourceAccount)
    }

    @Test fun multiHomeSourceTest2() {
        val sampleUser = AuthUserRecord(AccessToken("2257710474-XXXXXXXXXXXXXXX", "XXXXXXXXXXXXXXXX"))
        sampleUser.ScreenName = "yukari4a"
        val sampleUser2 = AuthUserRecord(AccessToken("26197127-XXXXXXXXXXXXXXX", "XXXXXXXXXXXXXXXX"))
        sampleUser2.ScreenName = "shibafu528"

        val source = parseSource("from home:\"yukari4a\", home:\"shibafu528\"", listOf(sampleUser, sampleUser2))
        assertEquals(2, source.size())
        assertEquals(Home::class.java, source.first().javaClass)
        assertEquals(Home::class.java, source.drop(1).first().javaClass)
        assertEquals(sampleUser, source.first().sourceAccount)
        assertEquals(sampleUser2, source.drop(1).first().sourceAccount)
    }

    private fun evaluateFake(snode: SNode) : Any {
        val result = snode.evaluate(FakeStatus(0), emptyList())
        println(snode)
        return result
    }

    @Test fun trueExpressionTest() {
        val snode = parseExpression("(t)", emptyList())
        val result = evaluateFake(snode)
        assertEquals(true, result)
    }

    @Test fun falseExpressionTest() {
        val snode = parseExpression("(nil)", emptyList())
        val result = evaluateFake(snode)
        assertEquals(false, result)
    }

    @Test fun andExpressionTest() {
        val snode = parseExpression("(and t t)", emptyList())
        val result = evaluateFake(snode)
        assertEquals(true, result)
    }

    @Test fun orExpressionTest() {
        val snode = parseExpression("(or t nil)", emptyList())
        val result = evaluateFake(snode)
        assertEquals(true, result)
    }

    @Test fun notExpressionTest() {
        val snode = parseExpression("(not t)", emptyList())
        val result = evaluateFake(snode)
        assertEquals(false, result)
    }

    @Test fun containsExpressionTest() {
        val snode = parseExpression("(contains \"ておくれとしぁ\" \"ておくれ\")", emptyList())
        val result = evaluateFake(snode)
        assertEquals(true, result)
    }

    @Test fun regexExpressionTest() {
        val snode = parseExpression("(regex \"らこらこらこ～ｗ\" \"(らこ){3}～+[wｗ]+\")", emptyList())
        val result = evaluateFake(snode)
        assertEquals(true, result)
    }

    @Test fun regexFailedExpressionTest() {
        val snode = parseExpression("(regex \"らこらこらこ～ｗ\" \"(みく){3}～+[wｗ]+\")", emptyList())
        val result = evaluateFake(snode)
        assertEquals(false, result)
    }

    @Test fun andContainsExpressionTest() {
        val toshi_a = "みくのおっぱいにおかおをうずめてすーはーすーはーいいかおり！"
        val snode = parseExpression("(and (contains \"$toshi_a\" \"みく\") (contains \"$toshi_a\" \"おかお\") (contains \"$toshi_a\" \"いいかおり\"))", emptyList())
        val result = evaluateFake(snode)
        assertEquals(true, result)
    }

    @Test fun andContainsFailedExpressionTest() {
        val surfboard = "ゆかりまないた"
        val snode = parseExpression("(and (contains \"$surfboard\" \"ゆかり\") (contains \"$surfboard\" \"きょにゅう\"))", emptyList())
        val result = evaluateFake(snode)
        assertEquals(false, result)
    }

    @Test fun simpleQueryTest() {
        val q = "from * where (t)"
        val filter = QueryCompiler.compile(emptyList(), q)
        assertEquals(All::class.java, filter.sources.first().javaClass)
        assertEquals(true, filter.evaluate(FakeStatus(0), emptyList()))
    }

    @Test fun emptyWhereQueryTest() {
        val q = "from * where"
        val filter = QueryCompiler.compile(emptyList(), q)
        assertEquals(All::class.java, filter.sources.first().javaClass)
        assertEquals(true, filter.evaluate(FakeStatus(0), emptyList()))
    }
}