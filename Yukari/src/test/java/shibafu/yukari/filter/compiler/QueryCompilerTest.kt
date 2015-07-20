package shibafu.yukari.filter.compiler

import shibafu.yukari.filter.source.All
import shibafu.yukari.filter.source.FilterSource
import java.lang.reflect.InvocationTargetException
import kotlin.test.assertEquals
import org.junit.Test as test

/**
 * Created by shibafu on 15/07/20.
 */
public class QueryCompilerTest {

    suppress("UNCHECKED_CAST")
    private fun parseSource(fromQuery: String): List<FilterSource> {
        val method = javaClass<QueryCompiler.Companion>().getDeclaredMethod("parseSource", javaClass<String>())
        method.setAccessible(true)
        return method.invoke(QueryCompiler.Companion, fromQuery) as List<FilterSource>
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
}