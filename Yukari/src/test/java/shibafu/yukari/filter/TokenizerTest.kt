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
        assertEquals(TokenType.String, fooToken.type)
        assertEquals("foo", fooToken.value)

        val barToken = tokenizer.next()
        assertEquals(TokenType.String, barToken.type)
        assertEquals("bar", barToken.value)

        val hogeFugaToken = tokenizer.next()
        assertEquals(TokenType.String, hogeFugaToken.type)
        assertEquals("hoge fuga", hogeFugaToken.value)

        assertFalse(tokenizer.hasNext())
    }

    test fun singleQuotationTokenTest() {
        val tokenizer = Tokenizer("'foo' \"bar\" 'hoge \"fuga\" piyo' \"hoge 'fuga' piyo\"")

        val fooToken = tokenizer.next()
        assertEquals(TokenType.String, fooToken.type)
        assertEquals("foo", fooToken.value)

        val barToken = tokenizer.next()
        assertEquals(TokenType.String, barToken.type)
        assertEquals("bar", barToken.value)

        val hogeFugaToken = tokenizer.next()
        assertEquals(TokenType.String, hogeFugaToken.type)
        assertEquals("hoge \"fuga\" piyo", hogeFugaToken.value)

        val hogeFugaToken2 = tokenizer.next()
        assertEquals(TokenType.String, hogeFugaToken2.type)
        assertEquals("hoge 'fuga' piyo", hogeFugaToken2.value)

        assertFalse(tokenizer.hasNext())
    }

    test fun noneTokenTest() {
        val tokenizer = Tokenizer("")
        assertFalse(tokenizer.hasNext())
    }

    test fun literalTokenTest() {
        val tokenizer = Tokenizer("foo bar")

        val fooToken = tokenizer.next()
        assertEquals(TokenType.Literal, fooToken.type)
        assertEquals("foo", fooToken.value)

        val barToken = tokenizer.next()
        assertEquals(TokenType.Literal, barToken.type)
        assertEquals("bar", barToken.value)

        assertFalse(tokenizer.hasNext())
    }

    test fun literalAndQuotationTest() {
        val tokenizer = Tokenizer("foo \"bar\" baz")

        val fooToken = tokenizer.next()
        assertEquals(TokenType.Literal, fooToken.type)
        assertEquals("foo", fooToken.value)

        val barToken = tokenizer.next()
        assertEquals(TokenType.String, barToken.type)
        assertEquals("bar", barToken.value)

        val bazToken = tokenizer.next()
        assertEquals(TokenType.Literal, bazToken.type)
        assertEquals("baz", bazToken.value)

        assertFalse(tokenizer.hasNext())
    }

    test fun commaTest() {
        val tokenizer = Tokenizer("hoge, fuga")

        val hogeToken = tokenizer.next()
        assertEquals(TokenType.Literal, hogeToken.type)
        assertEquals("hoge", hogeToken.value)

        val commaToken = tokenizer.next()
        assertEquals(TokenType.Comma, commaToken.type)

        val fugaToken = tokenizer.next()
        assertEquals(TokenType.Literal, fugaToken.type)
        assertEquals("fuga", fugaToken.value)
    }
}