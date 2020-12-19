package shibafu.yukari.filter.sexp

import shibafu.yukari.database.AuthUserRecord

/**
 * クエリ評価のパラメータや状態を保持します。
 *
 * @property target 評価対象オブジェクト
 * @property userRecords ユーザアカウント
 */
class EvaluateContext(val target: Any, val userRecords: List<AuthUserRecord>) {
    /**
     * 評価中に関数によって設定、あるいは呼び出し元によって追加で与えられる変数を格納します。
     *
     * この表に格納されている値は [VariableNode] において "$key" の形で参照することが出来ます。
     */
    var variables: MutableMap<String, Any?> = mutableMapOf()
}