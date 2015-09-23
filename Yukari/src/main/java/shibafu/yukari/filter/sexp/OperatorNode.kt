package shibafu.yukari.filter.sexp

import shibafu.yukari.twitter.AuthUserRecord
import twitter4j.TwitterResponse
import java.util.regex.Pattern

public class EqualsNode(override val children: List<SNode>) : SNode {
    override fun evaluate(status: TwitterResponse, userRecords: List<AuthUserRecord>): Any {
        return when (children.size()) {
            0 -> false
            1 -> children.first().equals(true)
            else -> children.drop(1).all { children.first().equals(it) }
        }
    }
}

public class NotEqualsNode(override val children: List<SNode>) : SNode {
    override fun evaluate(status: TwitterResponse, userRecords: List<AuthUserRecord>): Any {
        return !when (children.size()) {
            0 -> false
            1 -> children.first().equals(true)
            else -> children.drop(1).all { children.first().equals(it) }
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
        if (ev.size() != 2 || !ev.all { it is String }) return false

        return (ev.first() as String).contains(ev[1] as String)
    }
}

public class RegexNode(override val children: List<SNode>) : SNode {
    override fun evaluate(status: TwitterResponse, userRecords: List<AuthUserRecord>): Any {
        val ev = children.map { it.evaluate(status, userRecords) }
        if (ev.size() != 2 || !ev.all { it is String }) return false

        val source = ev.first() as String
        val matcher = Pattern.compile(ev.drop(1).first() as String).matcher(source)

        return matcher.matches()
    }
}

public class AddOperatorNode(override val children: List<SNode>) : SNode, FactorNode {
    override var value: Any? = null

    override fun evaluate(status: TwitterResponse, userRecords: List<AuthUserRecord>): Any {
        value = children.map { it.evaluate(status, userRecords).toString().toLong() }.fold(0L, {r, v -> r + v})

        return true;
    }

    override fun toString(): String {
        return super<FactorNode>.toString()
    }

}