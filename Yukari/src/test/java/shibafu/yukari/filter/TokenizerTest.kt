package shibafu.yukari.filter

import org.junit.Test
import shibafu.yukari.filter.compiler.TokenType
import shibafu.yukari.filter.compiler.Tokenizer
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Created by shibafu on 15/06/14.
 */
class TokenizerTest {
    @Test fun quotationTokenTest() {
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

    @Test fun singleQuotationTokenTest() {
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

    @Test fun noneTokenTest() {
        val tokenizer = Tokenizer("")
        assertFalse(tokenizer.hasNext())
    }

    @Test fun literalTokenTest() {
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

    @Test fun literalAndQuotationTest() {
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

    @Test fun commaTest() {
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

    @Test fun colonTest() {
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

    @Test fun parenthesisTest() {
        val tokenizer = Tokenizer("hoge: (fuga)")

        val hogeToken = tokenizer.next()
        assertEquals(TokenType.LITERAL, hogeToken.type)
        assertEquals("hoge", hogeToken.value)
        assertEquals(0, hogeToken.cursor)

        val colonToken = tokenizer.next()
        assertEquals(TokenType.COLON, colonToken.type)
        assertEquals(4, colonToken.cursor)

        val leftParenthesisToken = tokenizer.next()
        assertEquals(TokenType.LEFT_PARENTHESIS, leftParenthesisToken.type)
        assertEquals(6, leftParenthesisToken.cursor)

        val fugaToken = tokenizer.next()
        assertEquals(TokenType.LITERAL, fugaToken.type)
        assertEquals("fuga", fugaToken.value)
        assertEquals(7, fugaToken.cursor)

        val rightParenthesisToken = tokenizer.next()
        assertEquals(TokenType.RIGHT_PARENTHESIS, rightParenthesisToken.type)
        assertEquals(11, rightParenthesisToken.cursor)
    }
}