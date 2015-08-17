package shibafu.yukari.filter

import shibafu.yukari.filter.expression.Expression
import shibafu.yukari.filter.sexp.SNode
import shibafu.yukari.filter.sexp.ValueNode
import shibafu.yukari.filter.source.FilterSource
import shibafu.yukari.twitter.AuthUserRecord
import twitter4j.TwitterResponse

/**
 * コンパイルされた抽出クエリを表します。
 *
 * 抽出対象のデータソースとコンパイルされたクエリ式が含まれ、ツイートやメッセージを評価するために使用することが出来ます。
 *
 * Created by shibafu on 15/06/07.
 *
 * @property sources 抽出対象のデータソースです。
 * @property rootNode コンパイルされたクエリ式です。評価時に呼び出されます。
 * @constructor 抽出ソースとコンパイルされた式を格納するオブジェクトを生成します。このコンストラクタはクエリコンパイラ以外呼びだすべきではありません。
 */
public data class FilterQuery(public val sources: List<FilterSource>, private val rootNode: SNode) {
    /**
     * ツイートやメッセージをコンパイルされたクエリ式で評価します。
     * @param target 評価対象
     * @param userRecords ユーザアカウント (評価時にアカウント変数として使用されます)
     * @return クエリ式の評価結果 (抽出であれば、真となったら表示するのが妥当です)
     */
    public fun evaluate(target: TwitterResponse, userRecords: List<AuthUserRecord>): Boolean
            = rootNode.evaluate(target, userRecords).equals(true)

    companion object {

        /**
         * 抽出クエリをコード上で定義し、[FilterQuery]インスタンスを生成します。
         */
        public fun build(query: Builder.() -> Unit): FilterQuery {
            return Builder(query).toFilterQuery()
        }
    }

    /**
     * 抽出クエリをコード上で定義するためのDSLを提供します。
     */
    public class Builder(query: Builder.() -> Unit) {
        private var sources: List<FilterSource> = emptyList()
        private var rootNode: SNode = ValueNode(true)

        init {
            query()
        }

        /**
         * コンストラクタでの定義入力を利用し、[FilterQuery]インスタンスを生成します。
         */
        public fun toFilterQuery(): FilterQuery = FilterQuery(sources, rootNode)

        /**
         * FROM句を定義します。
         */
        public fun from(query: FromBuilder.() -> Unit) {
            sources += FromBuilder(query).sources
        }
    }

    /**
     * 抽出クエリのFROM句をコード上で定義するためのDSLを提供します。
     */
    public class FromBuilder(query: FromBuilder.() -> Unit) {
        public var sources: List<FilterSource> = emptyList()

        init {
            query()
        }

        /**
         * 抽出ソースを追加します。
         */
        public fun FilterSource.plus() {
            sources += this
        }
    }
}