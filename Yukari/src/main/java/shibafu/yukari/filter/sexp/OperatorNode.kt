package shibafu.yukari.filter.sexp

import java.util.regex.Pattern

/**
 * 等価演算関数ノード
 */
public class EqualsNode(override val children: List<SNode>) : SNode {
    constructor(vararg children: SNode) : this(children.asList())

    override fun evaluate(context: EvaluateContext): Any {
        if (children.isEmpty()) return false

        val first = children.first().evaluate(context)
        return when (children.size) {
            1 -> first.equals(true)
            else -> children.drop(1).all { first.equals(it.evaluate(context)) }
        }
    }
}

/**
 * 不等価演算関数ノード
 */
public class NotEqualsNode(override val children: List<SNode>) : SNode {
    constructor(vararg children: SNode) : this(children.asList())

    override fun evaluate(context: EvaluateContext): Any {
        if (children.isEmpty()) return true

        val first = children.first().evaluate(context)
        return !when (children.size) {
            1 -> first.equals(true)
            else -> children.drop(1).all { first.equals(it.evaluate(context)) }
        }
    }
}

/**
 * 否定演算関数ノード
 */
public class NotNode(override val children: List<SNode>) : SNode {
    constructor(vararg children: SNode) : this(children.asList())

    override fun evaluate(context: EvaluateContext): Any {
        return !(children.first().evaluate(context).let { if (it is Boolean) it else it.equals(true)});
    }
}

/**
 * 論理積演算関数ノード
 */
public class AndNode(override val children: List<SNode>) : SNode {
    constructor(vararg children: SNode) : this(children.asList())

    override fun evaluate(context: EvaluateContext): Any
            = children.map { it.evaluate(context).let { if (it is Boolean) it else it.equals(true) } }.all { it }
}

/**
 * 論理和演算関数ノード
 */
public class OrNode(override val children: List<SNode>) : SNode {
    constructor(vararg children: SNode) : this(children.asList())

    override fun evaluate(context: EvaluateContext): Any
            = children.map { it.evaluate(context).let { if (it is Boolean) it else it.equals(true) } }.any { it }
}

/**
 * 部分集合演算関数ノード
 */
public class ContainsNode(override val children: List<SNode>) : SNode {
    constructor(vararg children: SNode) : this(children.asList())

    override fun evaluate(context: EvaluateContext): Any {
        val ev = children.map { it.evaluate(context) }

        if (ev.size != 2) return false

        return when {
            ev.all { it is String } -> (ev.first() as String).contains(ev[1] as String)
            ev.first() is List<*> -> (ev.first() as List<*>).contains(ev[1])
            else -> false
        }
    }
}

/**
 * 正規表現マッチ関数ノード
 *
 * 1. 検査対象 [FactorNode]
 * 2. 正規表現パターン [FactorNode]
 */
public class RegexNode(override val children: List<SNode>) : SNode {
    var lastPatternString: String? = null
    var lastPattern: Pattern? = null

    constructor(vararg children: SNode) : this(children.asList())

    override fun evaluate(context: EvaluateContext): Any {
        val ev = children.map { it.evaluate(context) }
        if (ev.size != 2 || ev.any { it !is String }) return false

        val source = ev.first() as String
        val pattern = ev.drop(1).first() as String

        if (lastPatternString == null || lastPattern == null || !pattern.equals(lastPatternString)) {
            lastPattern = Pattern.compile(pattern)
            lastPatternString = pattern
        }

        return lastPattern?.matcher(source)?.find() ?: false
    }
}

/**
 * 加算関数ノード
 */
public class AddOperatorNode(override val children: List<SNode>) : SNode, FactorNode {
    override var value: Any? = null

    constructor(vararg children: SNode) : this(children.asList())

    override fun evaluate(context: EvaluateContext): Any {
        value = children.map { it.evaluate(context).toString().toLong() }.fold(0L, {r, v -> r + v})

        return value as Long
    }

    override fun toString(): String {
        return toExpression()
    }

    override fun toExpression(): String {
        return super<FactorNode>.toExpression()
    }

}

/**
 * 減算関数ノード
 */
public class SubtractOperatorNode(override val children: List<SNode>) : SNode, FactorNode {
    override var value: Any? = null

    constructor(vararg children: SNode) : this(children.asList())

    override fun evaluate(context: EvaluateContext): Any {
        value = children.map { it.evaluate(context).toString().toLong() }.fold(0L, {r, v -> r - v})

        return value as Long
    }

    override fun toString(): String {
        return toExpression()
    }

    override fun toExpression(): String {
        return super<FactorNode>.toExpression()
    }

}

/**
 * 乗算関数ノード
 */
public class MultiplyOperatorNode(override val children: List<SNode>) : SNode, FactorNode {
    override var value: Any? = null

    constructor(vararg children: SNode) : this(children.asList())

    override fun evaluate(context: EvaluateContext): Any {
        value = children.map { it.evaluate(context).toString().toLong() }.fold(0L, {r, v -> r * v})

        return value as Long
    }

    override fun toString(): String {
        return toExpression()
    }

    override fun toExpression(): String {
        return super<FactorNode>.toExpression()
    }

}

/**
 * 除算関数ノード
 */
public class DivideOperatorNode(override val children: List<SNode>) : SNode, FactorNode {
    override var value: Any? = null

    constructor(vararg children: SNode) : this(children.asList())

    override fun evaluate(context: EvaluateContext): Any {
        value = children.map { it.evaluate(context).toString().toLong() }.fold(0L, {r, v -> r / v})

        return value as Long
    }

    override fun toString(): String {
        return toExpression()
    }

    override fun toExpression(): String {
        return super<FactorNode>.toExpression()
    }

}

/**
 * 剰余演算関数ノード
 */
public class ModuloOperatorNode(override val children: List<SNode>) : SNode, FactorNode {
    override var value: Any? = null

    constructor(vararg children: SNode) : this(children.asList())

    override fun evaluate(context: EvaluateContext): Any {
        value = children.map { it.evaluate(context).toString().toLong() }.fold(0L, {r, v -> r % v})

        return value as Long
    }

    override fun toString(): String {
        return toExpression()
    }

    override fun toExpression(): String {
        return super<FactorNode>.toExpression()
    }

}