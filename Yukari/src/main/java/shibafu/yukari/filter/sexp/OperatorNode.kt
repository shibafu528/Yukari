package shibafu.yukari.filter.sexp

import shibafu.yukari.twitter.AuthUserRecord
import twitter4j.TwitterResponse
import java.util.regex.Pattern

public class EqualsNode(override val children: List<SNode>) : SNode {
    override fun evaluate(status: TwitterResponse, userRecords: List<AuthUserRecord>): Any {
        if (children.isEmpty()) return false

        val first = children.first().evaluate(status, userRecords)
        return when (children.size) {
            1 -> first.equals(true)
            else -> children.drop(1).all { first.equals(it.evaluate(status, userRecords)) }
        }
    }
}

public class NotEqualsNode(override val children: List<SNode>) : SNode {
    override fun evaluate(status: TwitterResponse, userRecords: List<AuthUserRecord>): Any {
        if (children.isEmpty()) return true

        val first = children.first().evaluate(status, userRecords)
        return !when (children.size) {
            1 -> first.equals(true)
            else -> children.drop(1).all { first.equals(it.evaluate(status, userRecords)) }
        }
    }
}

public class NotNode(override val children: List<SNode>) : SNode {
    override fun evaluate(status: TwitterResponse, userRecords: List<AuthUserRecord>): Any {
        return !(children.first().evaluate(status, userRecords).let { if (it is Boolean) it else it.equals(true)});
    }
}

public class AndNode(override val children: List<SNode>) : SNode {
    override fun evaluate(status: TwitterResponse, userRecords: List<AuthUserRecord>): Any
            = children.map { it.evaluate(status, userRecords).let { if (it is Boolean) it else it.equals(true) } }.all { it }
}

public class OrNode(override val children: List<SNode>) : SNode {
    override fun evaluate(status: TwitterResponse, userRecords: List<AuthUserRecord>): Any
            = children.map { it.evaluate(status, userRecords).let { if (it is Boolean) it else it.equals(true) } }.any { it }
}

public class ContainsNode(override val children: List<SNode>) : SNode {
    override fun evaluate(status: TwitterResponse, userRecords: List<AuthUserRecord>): Any {
        val ev = children.map { it.evaluate(status, userRecords) }
        if (ev.size != 2 || !ev.all { it is String }) return false

        return (ev.first() as String).contains(ev[1] as String)
    }
}

public class RegexNode(override val children: List<SNode>) : SNode {
    var lastPatternString: String? = null
    var lastPattern: Pattern? = null

    override fun evaluate(status: TwitterResponse, userRecords: List<AuthUserRecord>): Any {
        val ev = children.map { it.evaluate(status, userRecords) }
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

public class AddOperatorNode(override val children: List<SNode>) : SNode, FactorNode {
    override var value: Any? = null

    override fun evaluate(status: TwitterResponse, userRecords: List<AuthUserRecord>): Any {
        value = children.map { it.evaluate(status, userRecords).toString().toLong() }.fold(0L, {r, v -> r + v})

        return true;
    }

    override fun toString(): String {
        return toExpression()
    }

    override fun toExpression(): String {
        return super<FactorNode>.toExpression()
    }

}