package shibafu.yukari.filter.sexp

import com.google.common.collect.HashBasedTable
import com.google.common.collect.Table
import java.lang.reflect.Method

/**
 * 式ノード
 */
public interface SNode {
    val children: List<SNode>

    fun evaluate(context: EvaluateContext): Any

    fun toExpression(): String {
        val sb = StringBuilder("( ")
        sb.append(this.javaClass.simpleName.replace("Node", "").toLowerCase()).append("\n  ")
        children.forEach { sb.append( if (it is SNode) it.toExpression() else it.toString()); sb.append("\n  ") }
        sb.append(")")
        return sb.toString()
    }
}

/**
 * 因子ノード
 */
public interface FactorNode {
    var value: Any?

    fun toExpression(): String {
        return if (value is String) "\"${value.toString()}\"" else value.toString();
    }
}

/**
 * 定数ノード
 */
public class ValueNode(override var value: Any?) : SNode, FactorNode {
    override val children: List<SNode> = emptyList()

    override fun evaluate(context: EvaluateContext): Any = value ?: false

    override fun toString(): String {
        return toExpression()
    }

    override fun toExpression(): String {
        return super<FactorNode>.toExpression()
    }
}

/**
 * 変数ノード
 */
public class VariableNode(private val path: String) : SNode, FactorNode {
    override var value: Any? = null

    override val children: List<SNode> = emptyList()

    override fun evaluate(context: EvaluateContext): Any {
        tailrec fun invoke(pathList: List<String>, target: Any?): Any? {
            if (target == null) return null

            val targetClass = target.javaClass
            val methodName = pathList.first()

            val method = if (methodTable.contains(targetClass, methodName)) {
                methodTable[targetClass, methodName]
            } else {
                val normalizedMethodName = methodName.toLowerCase()
                val found = targetClass.methods.firstOrNull {
                    val name = it.name.toLowerCase()
                    name == "get$normalizedMethodName" || name == "is$normalizedMethodName"
                } ?: return null
                found.also { methodTable.put(targetClass, methodName, it) }
            }

            return when (pathList.size) {
                1 -> method.invoke(target)
                else -> invoke(pathList.drop(1), method.invoke(target))
            }
        }
        val pathList = path.split('.')
        value = if (path.startsWith('@')) {
            val screenName = pathList.first().substring(1)
            context.userRecords.firstOrNull{ it.ScreenName.equals(screenName) }.let { invoke(pathList.drop(1), it) }
        } else if (path.startsWith('$')) {
            val variable = context.variables[path.substring(1)]
            if (variable == null || pathList.size == 1) {
                variable
            } else {
                invoke(pathList.drop(1), variable)
            }
        } else {
            invoke(pathList, context.target)
        }
        return value ?: false
    }

    override fun toString(): String {
        return toExpression()
    }

    override fun toExpression(): String {
        return super<FactorNode>.toExpression()
    }

    companion object {
        private val methodTable: Table<Class<*>, String, Method> = HashBasedTable.create()
    }
}

/**
 * リストノード
 */
public class ListNode(override val children: List<SNode>) : SNode {

    override fun evaluate(context: EvaluateContext)
            = children.map { it.evaluate(context) }
}

/**
 * Quoteノード
 */
public class QuoteNode(override val children: List<SNode>) : SNode {

    override fun evaluate(context: EvaluateContext)
            = children
}