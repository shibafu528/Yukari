package shibafu.yukari.filter

import shibafu.yukari.filter.sexp.AndNode
import shibafu.yukari.filter.sexp.EqualsNode
import shibafu.yukari.filter.sexp.EvaluateContext
import shibafu.yukari.filter.sexp.OrNode
import shibafu.yukari.filter.sexp.SNode
import shibafu.yukari.filter.sexp.ValueNode
import shibafu.yukari.filter.sexp.VariableNode
import shibafu.yukari.filter.source.FilterSource
import shibafu.yukari.service.TwitterService
import shibafu.yukari.database.AuthUserRecord

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
data class FilterQuery(val sources: List<FilterSource>, private val rootNode: SNode) {
    /**
     * ツイートやメッセージをコンパイルされたクエリ式で評価します。
     * @param target 評価対象
     * @param userRecords ユーザアカウント (評価時にアカウント変数として使用されます)
     * @param variables 変数
     * @return クエリ式の評価結果 (抽出であれば、真となったら表示するのが妥当です)
     */
    fun evaluate(target: Any, userRecords: List<AuthUserRecord>, variables: Map<String, Any?> = emptyMap()): Boolean
            = AndNode(
                OrNode(
                    // RESTレスポンスは通す
                    EqualsNode(
                        VariableNode("\$passive"),
                        ValueNode(false)
                    ),
                    // UserStreamレスポンスは各ソースのフィルタを通す
                    OrNode(sources.map {
                        it.getStreamFilter()
                    })
                ),
                rootNode
            ).evaluate(EvaluateContext(target, userRecords).apply { this.variables.putAll(variables) }).equals(true)

    /**
     * いずれかのデータソースがStreamに接続された状態であるかチェックします。
     * @param service [TwitterService]
     */
    fun isConnectedAnyDynamicChannel(service: TwitterService): Boolean {
        sources.forEach { source ->
            val dcc = source.getDynamicChannelController() ?: return@forEach
            val userRecord = source.sourceAccount ?: return@forEach
            val stream = service.getProviderStream(userRecord) ?: return@forEach

            if (dcc.isConnected(service, stream)) {
                return true
            }
        }
        return false
    }

    /**
     * すべての接続可能なデータソースをStreamに接続します。
     * @param service [TwitterService]
     */
    fun connectAllDynamicChannel(service: TwitterService) {
        sources.forEach { source ->
            val dcc = source.getDynamicChannelController() ?: return@forEach
            val userRecord = source.sourceAccount ?: return@forEach
            val stream = service.getProviderStream(userRecord) ?: return@forEach
            dcc.connect(service, stream)
        }
    }

    /**
     * すべての接続可能なデータソースをStreamから切断します。
     * @param service [TwitterService]
     */
    fun disconnectAllDynamicChannel(service: TwitterService) {
        sources.forEach { source ->
            val dcc = source.getDynamicChannelController() ?: return@forEach
            val userRecord = source.sourceAccount ?: return@forEach
            val stream = service.getProviderStream(userRecord) ?: return@forEach
            dcc.disconnect(service, stream)
        }
    }

    companion object {
        val VOID_QUERY = FilterQuery(emptyList(), ValueNode(false))
        val VOID_QUERY_STRING = "from * where (false)"
    }
}