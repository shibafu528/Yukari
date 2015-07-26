package shibafu.yukari.filter.compiler

import android.util.Log
import shibafu.yukari.filter.FilterQuery
import shibafu.yukari.filter.expression.ConstantValue
import shibafu.yukari.filter.expression.Expression
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
        public val DEFAULT_QUERY: String = "from all"
        private val LOG_TAG = javaClass.getSimpleName()

        /**
         * クエリ文字列を解釈し、ソースリストと式オブジェクトにコンパイルします。
         * @param userRecords ソースリストに関連付けるユーザのリスト
         * @param query クエリ文字列
         * @return コンパイル済クエリ
         */
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
            val rootExpression = when {
                beginWhere < 0 -> ConstantValue(true) //where句が存在しない -> where trueと同義とする
                else -> parseExpression(query.substring(beginWhere), userRecords)
            }

            //コンパイル終了時間のログ出力
            Log.d(LOG_TAG, "Compile finished. (${System.currentTimeMillis() - compileTime} ms): $query")

            //コンパイル結果を格納
            return FilterQuery(sources, rootExpression)
        }

        private fun parseSource(fromQuery: String, userRecords: List<AuthUserRecord>): List<FilterSource> {
            class TempParams {
                var type: Token? = null
                var args: List<Token> = listOf()

                fun clear() {
                    type = null
                    args = listOf()
                }

                fun toFilterSource(): List<FilterSource> {
                    when(type!!.value) {
                        "all", "local", "*" -> return listOf(All())
                        "home" -> {
                            if (args.size() < 1) throw FilterCompilerException("アカウントが指定されていません。", type)
                            return args.map { p ->
                                Home(userRecords.firstOrNull { u -> p.value.equals(u.ScreenName) }
                                        ?: throw FilterCompilerException("この名前のアカウントは認証リスト内に存在しません。", p))
                            }
                        }
                    }
                    throw FilterCompilerException("抽出ソースの指定が正しくありません。", type)
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

        private fun parseExpression(whereQuery: String, userRecords: List<AuthUserRecord>): Expression {
            return ConstantValue(true)
        }
    }
}

public class FilterCompilerException(message: String, token: Token?) :
        Exception(if (token == null) "${message} : ?" else "${message} : ${token.value} (${token.cursor})")