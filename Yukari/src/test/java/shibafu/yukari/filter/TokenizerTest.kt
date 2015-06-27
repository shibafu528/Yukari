package shibafu.yukari.filter

import shibafu.yukari.filter.compiler.TokenType
import shibafu.yukari.filter.compiler.Tokenizer
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.junit.Test as test

/**
 * Created by shibafu on 15/06/14.
 */
class TokenizerTest {
    test fun quotationTokenTest() {
        val tokenizer = Tokenizer("\"foo\" \"bar\" \"hoge fuga\"")

        val fooToken = tokenizer.next()
        assertEquals(TokenType.STRING, fooToken.type)
        assertEquals("foo", fooToken.value)
        assertEquals(0, fooToken.cursor)

        val barToken = tokenizer.next()
        assertEquals(TokenType.STRING, barToken.type)
        assertEquals("bar", barToken.value)
        assertEquals(6, barToken.cursor)

        val hogeFugaToken = tokenizer.next()
        assertEquals(TokenType.STRING, hogeFugaToken.type)
        assertEquals("hoge fuga", hogeFugaToken.value)
        assertEquals(12, hogeFugaToken.cursor)

        assertFalse(tokenizer.hasNext())
    }

    test fun singleQuotationTokenTest() {
        val tokenizer = Tokenizer("'foo' \"bar\" 'hoge \"fuga\" piyo' \"hoge 'fuga' piyo\"")

        val fooToken = tokenizer.next()
        assertEquals(TokenType.STRING, fooToken.type)
        assertEquals("foo", fooToken.value)
        assertEquals(0, fooToken.cursor)

        val barToken = tokenizer.next()
        assertEquals(TokenType.STRING, barToken.type)
        assertEquals("bar", barToken.value)
        assertEquals(6, barToken.cursor)

        val hogeFugaToken = tokenizer.next()
        assertEquals(TokenType.STRING, hogeFugaToken.type)
        assertEquals("hoge \"fuga\" piyo", hogeFugaToken.value)
        assertEquals(12, hogeFugaToken.cursor)

        val hogeFugaToken2 = tokenizer.next()
        assertEquals(TokenType.STRING, hogeFugaToken2.type)
        assertEquals("hoge 'fuga' piyo", hogeFugaToken2.value)
        assertEquals(31, hogeFugaToken2.cursor)

        assertFalse(tokenizer.hasNext())
    }

    test fun noneTokenTest() {
        val tokenizer = Tokenizer("")
        assertFalse(tokenizer.hasNext())
    }

    test fun literalTokenTest() {
        val tokenizer = Tokenizer("foo bar")

        val fooToken = tokenizer.next()
        assertEquals(TokenType.LITERAL, fooToken.type)
        assertEquals("foo", fooToken.value)
        assertEquals(0, fooToken.cursor)

        val barToken = tokenizer.next()
        assertEquals(TokenType.LITERAL, barToken.type)
        assertEquals("bar", barToken.value)
        assertEquals(4, barToken.cursor)

        assertFalse(tokenizer.hasNext())
    }

    test fun literalAndQuotationTest() {
        val tokenizer = Tokenizer("foo \"bar\" baz")

        val fooToken = tokenizer.next()
        assertEquals(TokenType.LITERAL, fooToken.type)
        assertEquals("foo", fooToken.value)
        assertEquals(0, fooToken.cursor)

        val barToken = tokenizer.next()
        assertEquals(TokenType.STRING, barToken.type)
        assertEquals("bar", barToken.value)
        assertEquals(4, barToken.cursor)

        val bazToken = tokenizer.next()
        assertEquals(TokenType.LITERAL, bazToken.type)
        assertEquals("baz", bazToken.value)
        assertEquals(10, bazToken.cursor)

        assertFalse(tokenizer.hasNext())
    }

    test fun commaTest() {
        val tokenizer = Tokenizer("hoge, fuga")

        val hogeToken = tokenizer.next()
        assertEquals(TokenType.LITERAL, hogeToken.type)
        assertEquals("hoge", hogeToken.value)
        assertEquals(0, hogeToken.cursor)

        val commaToken = tokenizer.next()
        assertEquals(TokenType.COMMA, commaToken.type)
        assertEquals(4, commaToken.cursor)

        val fugaToken = tokenizer.next()
        assertEquals(TokenType.LITERAL, fugaToken.type)
        assertEquals("fuga", fugaToken.value)
        assertEquals(6, fugaToken.cursor)
    }

    test fun colonTest() {
        val tokenizer = Tokenizer("hoge: \"fuga\"")

        val hogeToken = tokenizer.next()
        assertEquals(TokenType.LITERAL, hogeToken.type)
        assertEquals("hoge", hogeToken.value)
        assertEquals(0, hogeToken.cursor)

        val colonToken = tokenizer.next()
        assertEquals(TokenType.COLON, colonToken.type)
        assertEquals(4, colonToken.cursor)

        val fugaToken = tokenizer.next()
        assertEquals(TokenType.STRING, fugaToken.type)
        assertEquals("fuga", fugaToken.value)
        assertEquals(6, fugaToken.cursor)
    }
}