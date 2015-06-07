package shibafu.yukari.filter.compiler

import android.util.Log
import shibafu.yukari.filter.FilterQuery
import shibafu.yukari.filter.expression.ConstantValue
import shibafu.yukari.filter.expression.Expression
import shibafu.yukari.filter.source.All
import shibafu.yukari.filter.source.FilterSource
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
                else -> parseSource(if (beginWhere < 0) query else query.substring(0, beginWhere - 1))
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

        private fun parseSource(fromQuery: String): List<FilterSource> {
            return listOf(All())
        }

        private fun parseExpression(whereQuery: String, userRecords: List<AuthUserRecord>): Expression {
            return ConstantValue(true)
        }
    }
}