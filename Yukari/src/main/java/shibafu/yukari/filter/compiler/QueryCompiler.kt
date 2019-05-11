package shibafu.yukari.filter.compiler

import shibafu.yukari.database.Provider
import shibafu.yukari.filter.FilterQuery
import shibafu.yukari.filter.sexp.AddOperatorNode
import shibafu.yukari.filter.sexp.AndNode
import shibafu.yukari.filter.sexp.ContainsNode
import shibafu.yukari.filter.sexp.DivideOperatorNode
import shibafu.yukari.filter.sexp.EqualsNode
import shibafu.yukari.filter.sexp.ListNode
import shibafu.yukari.filter.sexp.ModuloOperatorNode
import shibafu.yukari.filter.sexp.MultiplyOperatorNode
import shibafu.yukari.filter.sexp.NotEqualsNode
import shibafu.yukari.filter.sexp.NotNode
import shibafu.yukari.filter.sexp.OrNode
import shibafu.yukari.filter.sexp.QuoteNode
import shibafu.yukari.filter.sexp.RegexNode
import shibafu.yukari.filter.sexp.SNode
import shibafu.yukari.filter.sexp.SubtractOperatorNode
import shibafu.yukari.filter.sexp.ValueNode
import shibafu.yukari.filter.sexp.VariableNode
import shibafu.yukari.filter.source.All
import shibafu.yukari.filter.source.FilterSource
import shibafu.yukari.twitter.AuthUserRecord

/**
 * クエリ文字列を解釈し、ソースリストと式オブジェクトに変換する機能を提供します。
 *
 * Created by shibafu on 15/06/07.
 */
class QueryCompiler {
    private constructor()

    companion object {
        const val DEFAULT_QUERY: String = "from all"
        private val LOG_TAG = QueryCompiler::class.java.simpleName

        /**
         * クエリ文字列を解釈し、ソースリストと式オブジェクトにコンパイルします。
         * @param userRecords ソースリストに関連付けるユーザのリスト
         * @param query クエリ文字列
         * @return コンパイル済クエリ
         */
        @JvmStatic
        @Throws(FilterCompilerException::class, TokenizeException::class)
        fun compile(userRecords: List<AuthUserRecord>, query: String): FilterQuery {
            //コンパイル開始時間の記録
            val compileTime = System.currentTimeMillis()

            //query: null or empty -> 全抽出のクエリということにする
            val query = if (query.isEmpty()) DEFAULT_QUERY else query

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
                fun clear() {
                    type = null
                    args = listOf()
                }

                /** [typeValue]と[apiType]の組み合わせに対応する抽出ソースのクラスを検索 */
                private fun findSourceClass(typeValue: String, apiType: Int): Class<out FilterSource>? {
                    return Sources.MAP[typeValue]?.get(apiType)
                }

                /** [args]をアカウント指定文字列として解釈し、指定したソースで各アカウントの抽出ソースのインスタンスを作成します。 */
                private fun createFiltersWithAuthArguments(typeValue: String, requiredArgs: Boolean = false): List<FilterSource> {
                    val screenNames = if (args.isEmpty()) {
                        if (requiredArgs) {
                            throw FilterCompilerException("アカウントが指定されていません。", type)
                        }
                        userRecords.mapNotNull {
                            if (findSourceClass(typeValue, it.Provider.apiType) != null) {
                                Token(TokenType.STRING, 0, it.ScreenName)
                            } else {
                                null
                            }
                        }
                    } else {
                        args
                    }

                    return screenNames.map { p ->
                        val userRecord = (userRecords.firstOrNull { u -> p.value.equals(u.ScreenName) }
                                ?: throw FilterCompilerException("この名前のアカウントは認証リスト内に存在しません。", p))
                        val filterClz = findSourceClass(typeValue, userRecord.Provider.apiType)
                                ?: throw FilterCompilerException("この名前のアカウントではこのソースを使用できません。", p)
                        filterClz.getConstructor(AuthUserRecord::class.java).newInstance(userRecord)
                    }
                }

                /**
                 * [args]を / で分割し、ソースアカウントと引数として再解釈して抽出ソースのインスタンスを作成します。
                 * @param argumentCount 最低限必要な[args]の分割数。/で区切った時の分割数と比較します。
                 * @param requirePattern 引数エラー発生時にユーザに対して表示する引数の例を指定します。
                 */
                private fun createFiltersWithListArguments(typeValue: String, argumentCount: Int, requirePattern: String): List<FilterSource> {
                    if (args.isEmpty()) throw FilterCompilerException("引数が指定されていません。パターン：$requirePattern", type)
                    return args.map { p ->
                        val subArgs = p.value.split("/")

                        if (p.value.isBlank() || subArgs.size !in argumentCount..argumentCount + 1) {
                            throw FilterCompilerException("引数の要件を満たしていません。パターン：$requirePattern", p)
                        }

                        val (auth, secondArgument) = if (argumentCount == subArgs.size) {
                            if (subArgs.size == 2 && subArgs[1].toLongOrNull() != null) {
                                // argument == "receiver/long-id"
                                // Owner ScreenNameを空にするために "/" を頭につける
                                Pair(userRecords.firstOrNull { it.ScreenName == subArgs.first() }, "/" + subArgs[1])
                            } else {
                                // argument == "owner/*"
                                Pair(userRecords.firstOrNull { it.isPrimary }, p.value)
                            }
                        } else {
                            // argument == "receiver/*"
                            Pair(userRecords.firstOrNull { it.ScreenName == subArgs.first() }, subArgs.drop(1).joinToString("/"))
                        }

                        if (auth == null) {
                            throw FilterCompilerException("この名前のアカウントは認証リスト内に存在しません。", p)
                        }

                        val filterClz = findSourceClass(typeValue, auth.Provider.apiType)
                                ?: throw FilterCompilerException("この名前のアカウントではこのソースを使用できません。", p)
                        val constructor = filterClz.getConstructor(AuthUserRecord::class.java, String::class.java)
                        constructor.newInstance(auth, secondArgument)
                    }
                }

                /**
                 * [args]を / で分割し、ソースアカウントと引数として再解釈して抽出ソースのインスタンスを作成します。
                 *
                 * [createFiltersWithListArguments] のTraceソース特化版です。
                 * @param requirePattern 引数エラー発生時にユーザに対して表示する引数の例を指定します。
                 */
                private fun createFiltersWithTraceArguments(typeValue: String, requirePattern: String): List<FilterSource> {
                    if (args.isEmpty()) throw FilterCompilerException("引数が指定されていません。パターン：$requirePattern", type)
                    return args.map { p ->
                        val subArgs = p.value.split("/")

                        if (p.value.isBlank() || subArgs.size != 2) {
                            throw FilterCompilerException("引数の要件を満たしていません。パターン：$requirePattern", p)
                        }

                        val auth = userRecords.firstOrNull { it.ScreenName == subArgs[0] }
                            ?: throw FilterCompilerException("この名前のアカウントは認証リスト内に存在しません。", p)
                        val originId = subArgs[1]

                        val filterClz = findSourceClass(typeValue, auth.Provider.apiType)
                                ?: throw FilterCompilerException("この名前のアカウントではこのソースを使用できません。", p)
                        val constructor = filterClz.getConstructor(AuthUserRecord::class.java, String::class.java)
                        constructor.newInstance(auth, originId)
                    }
                }

                /**
                 * [args] からTwitter検索ソースのインスタンスを作成します。
                 */
                private fun createTwitterSearchFilters(typeValue: String): List<FilterSource> {
                    if (args.isEmpty()) throw FilterCompilerException("引数が指定されていません。", type)
                    return args.map { p ->
                        if (p.value.isBlank()) {
                            throw FilterCompilerException("検索キーワードを空にすることはできません。", p)
                        }

                        val auth = userRecords.firstOrNull { it.isPrimary && it.Provider.apiType == Provider.API_TWITTER }
                            ?: userRecords.firstOrNull { it.Provider.apiType == Provider.API_TWITTER }
                            ?: throw FilterCompilerException("使用可能なTwitterアカウントがありません。", p)

                        val filterClz = findSourceClass(typeValue, auth.Provider.apiType)
                                ?: throw FilterCompilerException("この名前のアカウントではこのソースを使用できません。", p)
                        val constructor = filterClz.getConstructor(AuthUserRecord::class.java, String::class.java)
                        constructor.newInstance(auth, p.value)
                    }
                }

                /** 構文解析の結果から抽出ソースのインスタンスを作成します。 */
                fun toFilterSource(): List<FilterSource> {
                    return when (type!!.value) {
                        "all", "local", "*", "stream" -> listOf(All())
                        "home" -> createFiltersWithAuthArguments("home")
                        "mention", "mentions", "reply", "replies" -> createFiltersWithAuthArguments("mention")
                        "user" -> createFiltersWithListArguments("user", 1, "(受信ユーザ/)対象ユーザ")
                        "list" -> createFiltersWithListArguments("list", 2, "(受信ユーザ/)ユーザ/リスト名")
                        "trace" -> createFiltersWithTraceArguments("trace", "受信ユーザ/起点ID")
                        "message", "messages", "dm", "dms" -> createFiltersWithAuthArguments("message")
                        "search" -> createTwitterSearchFilters("search")
                        "favorite", "favorites" -> createFiltersWithListArguments("favorite", 1, "(受信ユーザ/)対象ユーザ")
                        "don_local" -> createFiltersWithAuthArguments("don_local")
                        "don_anon_local" -> createFiltersWithListArguments("don_anon_local", 1, "インスタンス名")
                        "don_federated" -> createFiltersWithAuthArguments("don_federated")
                        "don_anon_federated" -> createFiltersWithListArguments("don_anon_federated", 1, "インスタンス名")
                        "don_hashtag" -> createFiltersWithTraceArguments("don_hashtag", "受信ユーザ/ハッシュタグ")
                        "don_local_hashtag" -> createFiltersWithTraceArguments("don_local_hashtag", "受信ユーザ/ハッシュタグ")

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
            return filters
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
                            else {
                                @Suppress("IMPLICIT_CAST_TO_ANY")
                                val value = token.value.toLongOrNull()?.let {
                                    if (Int.MIN_VALUE <= it && it <= Int.MAX_VALUE) it.toInt() else it
                                } ?: token.value.toDoubleOrNull() ?: token.value
                                return ValueNode(value)
                            }
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
                            "noteq", "neq", "!=", "/=" -> NotEqualsNode(paramList)
                            "contains", "in" -> ContainsNode(paramList)
                            "regex", "re", "rg" -> RegexNode(paramList)
                            "list" -> ListNode(paramList)
                            "quote" -> QuoteNode(paramList)
                            "+" -> AddOperatorNode(paramList)
                            "-" -> SubtractOperatorNode(paramList)
                            "*" -> MultiplyOperatorNode(paramList)
                            "/" -> DivideOperatorNode(paramList)
                            "%", "mod" -> ModuloOperatorNode(paramList)
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
            return if (whereQuery.isEmpty()) ValueNode(true) else recursiveParse(tokenizer)
        }
    }
}

class FilterCompilerException(message: String, token: Token?) : Exception(
        if (token == null) {
            "$message : ?"
        } else {
            "$message : ${token.value} (${token.cursor})"
        }
)