package shibafu.yukari.filter.compiler

import org.junit.Test
import shibafu.yukari.entity.MockStatus
import shibafu.yukari.filter.sexp.EvaluateContext
import shibafu.yukari.filter.sexp.SNode
import shibafu.yukari.filter.source.All
import shibafu.yukari.filter.source.FilterSource
import shibafu.yukari.filter.source.Home
import shibafu.yukari.database.AuthUserRecord
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
        assertEquals(0, source.size)
    }

    @Test fun allSourceTest() {
        val source = parseSource("from all")
        assertEquals(1, source.size)
        assertEquals<Class<*>>(All::class.java, source.first().javaClass)
    }

    @Test fun doubleAllSourceTest() {
        val source = parseSource("from all, all")
        assertEquals(2, source.size)
        assertEquals<Class<*>>(All::class.java, source.first().javaClass)
        assertEquals<Class<*>>(All::class.java, source.drop(1).first().javaClass)
    }

    @Test fun localSourceTest() {
        val source = parseSource("from local")
        assertEquals(1, source.size)
        assertEquals<Class<*>>(All::class.java, source.first().javaClass)
    }

    @Test fun asteriskSourceTest() {
        val source = parseSource("from *")
        assertEquals(1, source.size)
        assertEquals<Class<*>>(All::class.java, source.first().javaClass)
    }

    @Test(expected = FilterCompilerException::class) fun illegalSourceTest() {
        try {
            parseSource("from manaita")
        } catch (e: InvocationTargetException) {
            throw e.cause!!
        }
    }

    @Test fun homeSourceTest() {
        val sampleUser = AuthUserRecord(AccessToken("2257710474-XXXXXXXXXXXXXXX", "XXXXXXXXXXXXXXXX"))
        sampleUser.ScreenName = "yukari4a"

        val source = parseSource("from home:\"yukari4a\"", listOf(sampleUser))
        assertEquals(1, source.size)
        assertEquals<Class<*>>(Home::class.java, source.first().javaClass)
        assertEquals(sampleUser, source.first().sourceAccount)
    }

    @Test fun multiHomeSourceTest1() {
        val sampleUser = AuthUserRecord(AccessToken("2257710474-XXXXXXXXXXXXXXX", "XXXXXXXXXXXXXXXX"))
        sampleUser.ScreenName = "yukari4a"
        val sampleUser2 = AuthUserRecord(AccessToken("26197127-XXXXXXXXXXXXXXX", "XXXXXXXXXXXXXXXX"))
        sampleUser2.ScreenName = "shibafu528"

        val source = parseSource("from home:\"yukari4a\", \"shibafu528\"", listOf(sampleUser, sampleUser2))
        assertEquals(2, source.size)
        assertEquals<Class<*>>(Home::class.java, source.first().javaClass)
        assertEquals<Class<*>>(Home::class.java, source.drop(1).first().javaClass)
        assertEquals(sampleUser, source.first().sourceAccount)
        assertEquals(sampleUser2, source.drop(1).first().sourceAccount)
    }

    @Test fun multiHomeSourceTest2() {
        val sampleUser = AuthUserRecord(AccessToken("2257710474-XXXXXXXXXXXXXXX", "XXXXXXXXXXXXXXXX"))
        sampleUser.ScreenName = "yukari4a"
        val sampleUser2 = AuthUserRecord(AccessToken("26197127-XXXXXXXXXXXXXXX", "XXXXXXXXXXXXXXXX"))
        sampleUser2.ScreenName = "shibafu528"

        val source = parseSource("from home:\"yukari4a\", home:\"shibafu528\"", listOf(sampleUser, sampleUser2))
        assertEquals(2, source.size)
        assertEquals<Class<*>>(Home::class.java, source.first().javaClass)
        assertEquals<Class<*>>(Home::class.java, source.drop(1).first().javaClass)
        assertEquals(sampleUser, source.first().sourceAccount)
        assertEquals(sampleUser2, source.drop(1).first().sourceAccount)
    }

    private fun evaluateFake(snode: SNode) : Any {
        val context = EvaluateContext(FakeStatus(0), emptyList())
        val result = snode.evaluate(context)
        println(snode)
        return result
    }

    private fun evaluateFake(snode: SNode, text: String) : Any {
        val sampleUser = AuthUserRecord(AccessToken("2257710474-XXXXXXXXXXXXXXX", "XXXXXXXXXXXXXXXX"))
        sampleUser.ScreenName = "yukari4a"
        val status = object : MockStatus(0, sampleUser) {
            override val text: String = text
        }
        val context = EvaluateContext(status, emptyList())
        val result = snode.evaluate(context)
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

    @Test fun equalsNumericExpressionTest() {
        val snode = parseExpression("(= 1 1)", emptyList())
        val result = evaluateFake(snode)
        assertEquals(true, result)
    }

    @Test fun equalsStringExpressionTest() {
        val snode = parseExpression("(= hoge hoge)", emptyList())
        val result = evaluateFake(snode)
        assertEquals(true, result)
    }

    @Test fun notEqualsNumericExpressionTest() {
        val snode = parseExpression("(!= 1 2)", emptyList())
        val result = evaluateFake(snode)
        assertEquals(true, result)
    }

    @Test fun notEqualsStringExpressionTest() {
        val snode = parseExpression("(!= yukari kyonyu)", emptyList())
        val result = evaluateFake(snode)
        assertEquals(true, result)
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
        assertEquals<Class<*>>(All::class.java, filter.sources.first().javaClass)
        assertEquals(true, filter.evaluate(FakeStatus(0), emptyList()))
    }

    @Test fun emptyWhereQueryTest() {
        val q = "from * where"
        val filter = QueryCompiler.compile(emptyList(), q)
        assertEquals<Class<*>>(All::class.java, filter.sources.first().javaClass)
        assertEquals(true, filter.evaluate(FakeStatus(0), emptyList()))
    }

    @Test fun emptyWhereQueryTest2() {
        val q = "from * where ()"
        val filter = QueryCompiler.compile(emptyList(), q)
        assertEquals<Class<*>>(All::class.java, filter.sources.first().javaClass)
        assertEquals(false, filter.evaluate(FakeStatus(0), emptyList()))
    }

    @Test fun returnedQueryTest() {
        val q = "from * \nwhere (t)"
        val filter = QueryCompiler.compile(emptyList(), q)
        assertEquals<Class<*>>(All::class.java, filter.sources.first().javaClass)
        assertEquals(true, filter.evaluate(FakeStatus(0), emptyList()))
    }

    @Test fun doubleSpaceQueryTest() {
        val q = "from *  where (t)"
        val filter = QueryCompiler.compile(emptyList(), q)
        assertEquals<Class<*>>(All::class.java, filter.sources.first().javaClass)
        assertEquals(true, filter.evaluate(FakeStatus(0), emptyList()))
    }

    @Test fun variableExpressionText() {
        val snode = parseExpression("(contains ?text \"まないた\")", emptyList())
        val result = evaluateFake(snode, "ゆかりまないた")
        assertEquals(true, result)
    }

    @Test fun variableRegexExpressionTest() {
        val snode = parseExpression("(regex ?text \"(らこ){3}～+[wｗ]+\")", emptyList())
        val result = evaluateFake(snode, "らこらこらこ～ｗ")
        assertEquals(true, result)
    }

    @Test fun integerVariableExpressionText() {
        val snode = parseExpression("(= ?providerApiType -1)", emptyList())
        val result = evaluateFake(snode, "")
        assertEquals(true, result)
    }

}
