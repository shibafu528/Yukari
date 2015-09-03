package shibafu.yukari.filter.compiler

import shibafu.yukari.filter.source.All
import shibafu.yukari.filter.source.FilterSource
import shibafu.yukari.filter.source.Home
import shibafu.yukari.twitter.AuthUserRecord
import twitter4j.auth.AccessToken
import java.lang.reflect.InvocationTargetException
import kotlin.test.assertEquals
import org.junit.Test as test

/**
 * Created by shibafu on 15/07/20.
 */
public class QueryCompilerTest {

    suppress("UNCHECKED_CAST")
    private fun parseSource(fromQuery: String, userRecords: List<AuthUserRecord> = emptyList()): List<FilterSource> {
        val method = javaClass<QueryCompiler.Companion>().getDeclaredMethod("parseSource", javaClass<String>(), javaClass<List<AuthUserRecord>>())
        method.setAccessible(true)
        return method.invoke(QueryCompiler.Companion, fromQuery, userRecords) as List<FilterSource>
    }

    test fun emptySourceTest() {
        val source = parseSource("from")
        assertEquals(1, source.size())
        assertEquals(javaClass<All>(), source.first().javaClass)
    }

    test fun allSourceTest() {
        val source = parseSource("from all")
        assertEquals(1, source.size())
        assertEquals(javaClass<All>(), source.first().javaClass)
    }

    test fun doubleAllSourceTest() {
        val source = parseSource("from all, all")
        assertEquals(2, source.size())
        assertEquals(javaClass<All>(), source.first().javaClass)
        assertEquals(javaClass<All>(), source.drop(1).first().javaClass)
    }

    test fun localSourceTest() {
        val source = parseSource("from local")
        assertEquals(1, source.size())
        assertEquals(javaClass<All>(), source.first().javaClass)
    }

    test fun asteriskSourceTest() {
        val source = parseSource("from *")
        assertEquals(1, source.size())
        assertEquals(javaClass<All>(), source.first().javaClass)
    }

    test(expected = FilterCompilerException::class) fun illegalSourceTest() {
        try {
            parseSource("from manaita")
        } catch (e: InvocationTargetException) {
            throw e.getCause()
        }
    }

    test fun homeSourceTest() {
        val sampleUser = AuthUserRecord(AccessToken("2257710474-XXXXXXXXXXXXXXX", "XXXXXXXXXXXXXXXX"))
        sampleUser.ScreenName = "yukari4a"

        val source = parseSource("from home:\"yukari4a\"", listOf(sampleUser))
        assertEquals(1, source.size())
        assertEquals(javaClass<Home>(), source.first().javaClass)
        assertEquals(sampleUser, source.first().sourceAccount)
    }

    test fun multiHomeSourceTest1() {
        val sampleUser = AuthUserRecord(AccessToken("2257710474-XXXXXXXXXXXXXXX", "XXXXXXXXXXXXXXXX"))
        sampleUser.ScreenName = "yukari4a"
        val sampleUser2 = AuthUserRecord(AccessToken("26197127-XXXXXXXXXXXXXXX", "XXXXXXXXXXXXXXXX"))
        sampleUser2.ScreenName = "shibafu528"

        val source = parseSource("from home:\"yukari4a\", \"shibafu528\"", listOf(sampleUser, sampleUser2))
        assertEquals(2, source.size())
        assertEquals(javaClass<Home>(), source.first().javaClass)
        assertEquals(javaClass<Home>(), source.drop(1).first().javaClass)
        assertEquals(sampleUser, source.first().sourceAccount)
        assertEquals(sampleUser2, source.drop(1).first().sourceAccount)
    }

    test fun multiHomeSourceTest2() {
        val sampleUser = AuthUserRecord(AccessToken("2257710474-XXXXXXXXXXXXXXX", "XXXXXXXXXXXXXXXX"))
        sampleUser.ScreenName = "yukari4a"
        val sampleUser2 = AuthUserRecord(AccessToken("26197127-XXXXXXXXXXXXXXX", "XXXXXXXXXXXXXXXX"))
        sampleUser2.ScreenName = "shibafu528"

        val source = parseSource("from home:\"yukari4a\", home:\"shibafu528\"", listOf(sampleUser, sampleUser2))
        assertEquals(2, source.size())
        assertEquals(javaClass<Home>(), source.first().javaClass)
        assertEquals(javaClass<Home>(), source.drop(1).first().javaClass)
        assertEquals(sampleUser, source.first().sourceAccount)
        assertEquals(sampleUser2, source.drop(1).first().sourceAccount)
    }
}