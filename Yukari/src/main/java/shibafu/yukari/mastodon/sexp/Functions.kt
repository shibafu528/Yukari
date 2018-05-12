package shibafu.yukari.mastodon.sexp

import android.net.Uri
import shibafu.yukari.filter.sexp.EvaluateContext
import shibafu.yukari.filter.sexp.SNode

/**
 * URLのホスト部が指定の文字列と一致しているか判定
 *
 * (URL HOST)
 */
class UrlHostEqualPredicate(override val children: List<SNode>) : SNode {
    constructor(vararg children: SNode) : this(children.asList())

    override fun evaluate(context: EvaluateContext): Any {
        if (children.size < 2) return false

        val first = children[0].evaluate(context)
        val second = children[1].evaluate(context)

        return second.toString() == Uri.parse(first.toString()).host
    }
}