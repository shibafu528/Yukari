package shibafu.yukari.filter.sexp

import shibafu.yukari.twitter.AuthUserRecord
import twitter4j.TwitterResponse

/**
 * 子ノードのうち因子ノードをLong値として抽出します。
 */
private fun List<SNode>.asLongFactors() : List<Long> {
    return this.filter { it is FactorNode }.map { it as FactorNode }.map {
        when (it.value) {
            is Number -> (it.value as Long)
            is String -> (it.value as String).toLong()
            else -> 0L
        }
    }
}

public class AddOperatorNode(override val children: List<SNode>) : SNode, FactorNode {
    override var value: Any? = null

    override fun evaluate(status: TwitterResponse, userRecords: List<AuthUserRecord>): Boolean {
        if (!children.all { it.evaluate(status, userRecords) }) return false;

        value = children.asLongFactors().fold(0L, {r, v -> r + v})

        return true;
    }

}