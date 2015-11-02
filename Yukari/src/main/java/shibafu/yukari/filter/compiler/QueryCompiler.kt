package shibafu.yukari.filter.compiler

import shibafu.yukari.filter.FilterQuery
import shibafu.yukari.filter.sexp.*
import shibafu.yukari.filter.source.All
import shibafu.yukari.filter.source.FilterSource
import shibafu.yukari.filter.source.Home
import shibafu.yukari.twitter.AuthUserRecord

/**
 * クエリ文字列を解釈し、ソースリストと式オブジェクトに変換する機能を提供します。
 *
 * Created by shibafu on 15/06/07.
 */
public final class QueryCompiler {
    private constructor()

    companion object {
        const public val DEFAULT_QUERY: String = "from all"
        private val LOG_TAG = QueryCompiler::class.java.simpleName

        /**
         * クエリ文字列を解釈し、ソースリストと式オブジェクトにコンパイルします。
         * @param userRecords ソースリストに関連付けるユーザのリスト
         * @param query クエリ文字列
         * @return コンパイル済クエリ
         */
        @JvmStatic
        @Throws(FilterCompilerException::class, TokenizeException::class)
        public fun compile(userRecords: List<AuthUserRecord>, query: String): FilterQuery {
            //コンパイル開始時間の記録
            val compileTime = System.currentTimeMillis()

            //query: null or empty -> 全抽出のクエリということにする
            val query = if (query.isNullOrEmpty()) DEFAULT_QUERY else query

            //from句とwhere句の開始位置と存在チェック
            val beginFrom = query.indexOf("from")
            val beginWhere = query.indexOf("where")

            //from句の解釈
            val sources = when {
                beginFrom < 0 -> listOf(All()) //from句が存在しない -> from allと同義とする
                else -> parseSource(if (beginWhere < 0) query else query.substring(0, beginWhere - 1), userRecords)
            }

            //where句の解釈
            val rootNode = when {
                beginWhere < 0 -> ValueNode(true) //where句が存在しない -> where trueと同義とする
                else -> parseExpression(query.substring(beginWhere).replaceFirst("where", "").trim(), userRecords)
            }

            //コンパイル終了時間のログ出力
//            Log.d(LOG_TAG, "Compile finished. (${System.currentTimeMillis() - compileTime} ms): $query")

            //コンパイル結果を格納
            return FilterQuery(sources, rootNode)
        }

        /**
         * FROM句の構文解析を行います。
         *
         * 事前にクエリ文字列からFROM句のみを抽出している必要があります。
         *
         * @param fromQuery クエリ文字列のFROM句
         * @param userRecords ソースリストに関連付けるユーザのリスト
         * @return 抽出ソースのリスト
         */
        @Throws(FilterCompilerException::class)
        private fun parseSource(fromQuery: String, userRecords: List<AuthUserRecord>): List<FilterSource> {
            class TempParams {
                var type: Token? = null
                var args: List<Token> = listOf()

                /** 解析結果をリセットします。 */
                public fun clear() {
                    type = null
                    args = listOf()
                }

                /** [args]をアカウント指定文字列として解釈し、指定したソースで各アカウントの抽出ソースのインスタンスを作成します。 */
                private fun <T : FilterSource> createFiltersWithAuthArguments(filterClz: Class<T>): List<T> {
                    if (args.size < 1) throw FilterCompilerException("アカウントが指定されていません。", type)

                    val constructor = filterClz.getConstructor(AuthUserRecord::class.java)
                    return args.map { p ->
                        constructor.newInstance(userRecords.firstOrNull { u -> p.value.equals(u.ScreenName) }
                                ?: throw FilterCompilerException("この名前のアカウントは認証リスト内に存在しません。", p))
                    }
                }

                /** 構文解析の結果から抽出ソースのインスタンスを作成します。 */
                public fun toFilterSource(): List<FilterSource> {
                    return when (type!!.value) {
                        "all", "local", "*", "stream" -> listOf(All())
                        "home" -> createFiltersWithAuthArguments(Home::class.java)

                        else -> throw FilterCompilerException("抽出ソースの指定が正しくありません。", type)
                    }
                }
            }

            var filters = emptyList<FilterSource>()
            var temp = TempParams()
            var needNextToken: Array<TokenType>? = null

            for (token in Tokenizer(fromQuery)) {
                //fromが入り込んでいたら飛ばす
                if (token.type == TokenType.LITERAL && "from".equals(token.value)) continue

                //フィルタの変化
                if (temp.type != null && token.type == TokenType.LITERAL) {
                    filters += temp.toFilterSource()
                    temp.clear()
                    needNextToken = null
                }

                //必須トークンチェック
                if (needNextToken != null) {
                    if (needNextToken.none { it == token.type }) {
                        throw FilterCompilerException("不正なトークンが検出されました。文法が正しいか確認して下さい。", token)
                    }
                    needNextToken = null
                    continue
                }

                if (temp.type == null) {
                    //抽出ソースのキャプチャ
                    if (token.type != TokenType.LITERAL) {
                        throw FilterCompilerException("抽出ソースが指定されていません。", token)
                    }
                    temp.type = token
                    needNextToken = arrayOf(TokenType.COLON, TokenType.COMMA)
                } else {
                    //引数のキャプチャ
                    if (token.type != TokenType.STRING) {
                        throw FilterCompilerException("引数が正しく指定されていません。", token)
                    }
                    temp.args += token
                    needNextToken = arrayOf(TokenType.COMMA)
                }
            }
            if (temp.type != null) {
                filters += temp.toFilterSource()
            }
            return when {
                filters.isEmpty() -> listOf(All())
                else -> filters
            }
        }

        @Throws(FilterCompilerException::class)
        private fun parseExpression(whereQuery: String, userRecords: List<AuthUserRecord>): SNode {
            fun tokenToNode(token: Token) : SNode {
                return if (token.type == TokenType.STRING) {
                    ValueNode(token.value)
                } else if (token.type == TokenType.LITERAL) {
                    when (token.value) {
                        "t", "true", "True", "TRUE" -> ValueNode(true)
                        "nil", "false", "f", "False", "FALSE" -> ValueNode(false)
                        else -> {
                            if (token.value.startsWith("?") && token.value.length > 1) return VariableNode(token.value.replaceFirst("?", ""))
                            else return ValueNode(token.value)
                        }
                    }
                } else {
                    throw FilterCompilerException("不正なトークンが検出されました。文法が正しいか確認して下さい。", token)
                }
            }

            fun recursiveParse(tokenizer: Tokenizer) : SNode {
                if (!tokenizer.hasNext()) throw FilterCompilerException("式を閉じるかっこが見つかりませんでした。", null)

                val funcToken = tokenizer.next()
                if (funcToken.type == TokenType.RIGHT_PARENTHESIS) return ValueNode(false)

                var paramList = emptyList<SNode>()
                for (token in tokenizer) {
                    if (token.type == TokenType.RIGHT_PARENTHESIS) {
                        if (paramList.isEmpty()) return tokenToNode(funcToken)
                        else return when(funcToken.value) {
                            "and", "&" -> AndNode(paramList)
                            "or", "|" -> OrNode(paramList)
                            "not", "!" -> NotNode(paramList)
                            "equals", "eq", "=", "==" -> EqualsNode(paramList)
                            "noteq", "!=", "/=" -> NotEqualsNode(paramList)
                            "contains", "in" -> ContainsNode(paramList)
                            "regex" -> RegexNode(paramList)
                            else -> throw FilterCompilerException("未定義の関数呼び出しです。", funcToken)
                        }
                    }

                    if (token.type == TokenType.LEFT_PARENTHESIS) {
                        paramList += recursiveParse(tokenizer)
                    } else {
                        paramList += tokenToNode(token)
                    }
                }
                throw FilterCompilerException("式を閉じるかっこが見つかりませんでした。", null)
            }

            val tokenizer = Tokenizer(whereQuery)
            if (tokenizer.hasNext()) tokenizer.next()
            return if (whereQuery.isNullOrEmpty()) ValueNode(true) else recursiveParse(tokenizer)
        }
    }
}

public class FilterCompilerException(message: String, token: Token?) :
        Exception(if (token == null) "$message : ?" else "$message : ${token.value} (${token.cursor})")