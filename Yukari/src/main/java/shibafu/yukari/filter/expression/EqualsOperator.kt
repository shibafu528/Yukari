package shibafu.yukari.filter.expression

import shibafu.yukari.twitter.AuthUserRecord
import twitter4j.TwitterResponse

/**
 * Created by shibafu on 15/06/07.
 */
public class EqualsOperator : OperatorExpression {
    override val leftExpression: Expression
    override val rightExpression: Expression

    constructor(left: Expression, right: Expression) {
        leftExpression = left
        rightExpression = right
    }

    override fun evaluate(status: TwitterResponse, userRecords: List<AuthUserRecord>): Boolean {
        fun valEvaluate(e: Expression): Any? = if (e is ValueExpression) e.let { it.evaluate(status, userRecords); it.value } else e.evaluate(status, userRecords)

        val leftValue = valEvaluate(leftExpression)
        val rightValue = valEvaluate(rightExpression)

        return when {
            leftValue != null -> leftValue.equals(rightValue)
            rightValue != null -> rightValue.equals(leftValue)
            else -> true
        }
    }

}