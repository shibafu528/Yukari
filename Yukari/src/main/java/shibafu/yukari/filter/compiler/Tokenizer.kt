package shibafu.yukari.filter.compiler

/**
 * Created by shibafu on 15/06/07.
 */
public class Tokenizer(private val query: String) : Iterator<Token> {
    private var currentPos: Int = 0

    override fun next(): Token {
        do {
            when (query[currentPos]) {
                '"' -> {

                }
            }
        } while (hasNext())
        throw UnsupportedOperationException()
    }

    override fun hasNext(): Boolean = currentPos < query.length()

    fun getQuoteString(start: Int) : String {
        var cursor = start
        while (cursor < query.length()) {
            when (query[cursor]) {
                // エスケープ文字 -> 飛ばす
                '\\' -> if (cursor + 1 == query.length()) {
                    throw TokenizeException(TokenizeExceptionKind.STRING_IS_NOT_CLOSED, query, cursor)
                } else cursor++

                // ダブルクォーテーション -> 探索終了
                '"' -> return query.substring(start, cursor - 1).replace("\\", "")
            }
            cursor++
        }
        //終端文字が見つからなかった場合
        throw TokenizeException(TokenizeExceptionKind.STRING_IS_NOT_CLOSED, query, cursor)
    }
}

public data class Token(val type: TokenType, val value: String) {
    fun isMatch(v: String) = v.toLowerCase().equals(value.toLowerCase())
}

public enum class TokenType {
    Literal,
    String
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