package shibafu.yukari.filter.sexp

import shibafu.yukari.twitter.AuthUserRecord
import twitter4j.TwitterResponse

/**
 * 式ノード
 */
public interface SNode {
    val children: List<SNode>;

    fun evaluate(status: TwitterResponse, userRecords: List<AuthUserRecord>): Any

    override fun toString(): String {
        val sb = StringBuilder("( ")
        sb.append(this.javaClass.getSimpleName().replace("Node", "").toLowerCase()).append("\n  ")
        children.forEach { sb.append(it.toString()); sb.append("\n  ") }
        sb.append(")")
        return sb.toString()
    }
}

/**
 * 因子ノード
 */
public interface FactorNode {
    var value: Any?

    override fun toString(): String {
        return if (value is String) "\"${value.toString()}\"" else value.toString();
    }
}

/**
 * 定数ノード
 */
public class ValueNode(override var value: Any?) : SNode, FactorNode {
    override val children: List<SNode> = emptyList()

    override fun evaluate(status: TwitterResponse, userRecords: List<AuthUserRecord>): Any = value ?: false

    override fun toString(): String {
        return super<FactorNode>.toString()
    }
}

/**
 * 変数ノード
 */
public class VariableNode(private val path: String) : SNode, FactorNode {
    override var value: Any? = null

    override val children: List<SNode> = emptyList()

    override fun evaluate(status: TwitterResponse, userRecords: List<AuthUserRecord>): Any {
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
        return value ?: false
    }

    override fun toString(): String {
        return super<FactorNode>.toString()
    }
}

/**
 * リストノード
 */
public class QuoteNode(override val children: List<SNode>) : SNode {

    override fun evaluate(status: TwitterResponse, userRecords: List<AuthUserRecord>)
            = children.map { it.evaluate(status, userRecords) }
}