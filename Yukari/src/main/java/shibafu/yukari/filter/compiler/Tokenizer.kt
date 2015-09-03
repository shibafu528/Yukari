package shibafu.yukari.filter.compiler

/**
 * Created by shibafu on 15/06/07.
 */
public class Tokenizer(private val query: String) : Iterator<Token> {
    private var currentPos: Int = 0

    override fun next(): Token {
        do {
            when (query[currentPos]) {
                ' ', '\t', '\r', '\n' -> {}
                '"' -> return Token(TokenType.STRING, currentPos, getQuoteString(currentPos + 1, '"'))
                '\'' -> return Token(TokenType.STRING, currentPos, getQuoteString(currentPos + 1, '\''))
                ',' -> return Token(TokenType.COMMA, currentPos++)
                ':' -> return Token(TokenType.COLON, currentPos++)
                '(' -> return Token(TokenType.LEFT_PARENTHESIS, currentPos++)
                ')' -> return Token(TokenType.RIGHT_PARENTHESIS, currentPos++)
                else -> {
                    val begin = currentPos

                    do {
                        if (" \"\t\r\n',:()".contains(query[currentPos])) break
                        currentPos++
                    } while (hasNext())

                    return Token(TokenType.LITERAL, begin, query.substring(begin, currentPos))
                }
            }
            currentPos++
        } while (hasNext())
        throw UnsupportedOperationException()
    }

    override fun hasNext(): Boolean = currentPos < query.length()

    private fun getQuoteString(start: Int, quoteToken: Char) : String {
        var cursor = start
        while (cursor < query.length()) {
            when (query[cursor]) {
                // エスケープ文字 -> 飛ばす
                '\\' -> if (cursor + 1 == query.length()) {
                    throw TokenizeException(TokenizeExceptionKind.STRING_IS_NOT_CLOSED, query, cursor)
                } else cursor++

                // クォーテーショントークン -> 探索終了
                quoteToken -> {
                    currentPos = cursor + 1
                    return query.substring(start, cursor).replace("\\", "")
                }
            }
            cursor++
        }
        //終端文字が見つからなかった場合
        throw TokenizeException(TokenizeExceptionKind.STRING_IS_NOT_CLOSED, query, cursor)
    }
}

/**
 * 字句解析を行った結果の字句単位です。
 *
 * @property type トークンの型([TokenType])を表します。
 * @property cursor トークンがソース文字列上に存在する位置を指します。
 * @property value トークンの文字列を表します。
 */
public data class Token(val type: TokenType, val cursor: Int, val value: String = "") {
    /**
     * 与えられた文字列が、このトークンの保有する文字列と大小文字を問わずに一致するかを照合します。
     *
     * @param v 比較対象の文字列
     */
    fun isMatch(v: String) = v.toLowerCase().equals(value.toLowerCase())
}

/**
 * 解析されたトークンの型を表します。
 */
public enum class TokenType {
    /** 他の区切り記号等で分割された、引用符の無い文字列です。 */
    LITERAL,
    /** 引用符によって囲われた文字列です。 */
    STRING,
    /** "," を表します。 */
    COMMA,
    /** ":" を表します。 */
    COLON,
    /** "(" を表します。 */
    LEFT_PARENTHESIS,
    /** ")" を表します。 */
    RIGHT_PARENTHESIS
}

public class TokenizeException(kind: TokenizeExceptionKind, query: String, cursor: Int)
    : Exception(kind.toMessage(query, cursor)) {}

public enum class TokenizeExceptionKind(val message: String) {
    STRING_IS_NOT_CLOSED("文字列が閉じられていません。"),
    ;

    public fun toMessage(query: String, cursor: Int): String {
        val highlight: String = when (this) {
            else -> if (cursor == query.length()) query.last().toString() else query[cursor].toString()
        }
        return "Pos:$cursor -> $highlight : $message"
    }
}