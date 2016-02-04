package shibafu.yukari.filter

import shibafu.yukari.filter.sexp.AndNode
import shibafu.yukari.filter.sexp.SNode
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
            = AndNode(listOf(
                AndNode(sources.map {
                    it.filterUserStream()
                }),
                rootNode
            )).evaluate(target, userRecords).equals(true)

}