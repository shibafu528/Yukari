package shibafu.yukari.filter.sexp

import shibafu.yukari.twitter.AuthUserRecord
import twitter4j.TwitterResponse

/**
 * 式ノード
 */
public interface SNode {
    val children: List<SNode>;

    fun evaluate(status: TwitterResponse, userRecords: List<AuthUserRecord>): Boolean
}

/**
 * 因子ノード
 */
public interface FactorNode {
    var value: Any?
}

/**
 * 定数ノード
 */
public class ValueNode(override var value: Any?) : SNode, FactorNode {
    override val children: List<SNode> = emptyList()

    override fun evaluate(status: TwitterResponse, userRecords: List<AuthUserRecord>): Boolean
        = if (value is Boolean) value as Boolean else true
}

/**
 * 変数ノード
 */
public class VariableNode(val path: String) : SNode, FactorNode {
    override var value: Any? = null

    override val children: List<SNode> = emptyList()

    override fun evaluate(status: TwitterResponse, userRecords: List<AuthUserRecord>): Boolean {
        fun invoke(pathList: List<String>, target: Any?) {
            if (target == null) return

            val method = target.javaClass.getMethods().first { it.getName().toLowerCase().equals("get" + pathList.first().toLowerCase()) }

            if (pathList.size() == 1) method.invoke(target)
            else invoke(pathList.drop(1), method.invoke(target))
        }
        val pathList = path.split('.').toArrayList()
        value = if (path.startsWith('@')) {
            val screenName = pathList.first().substring(1)
            userRecords.firstOrNull{ it.ScreenName.equals(screenName) }.let { invoke(pathList.drop(1), it) }
        } else {
            invoke(pathList, status)
        }
        return if (value is Boolean) value as Boolean else true
    }
}

/**
 * リストノード
 */
public class QuoteNode(override val children: List<SNode>) : SNode {

    override fun evaluate(status: TwitterResponse, userRecords: List<AuthUserRecord>)
            = children.all { it.evaluate(status, userRecords) }
}