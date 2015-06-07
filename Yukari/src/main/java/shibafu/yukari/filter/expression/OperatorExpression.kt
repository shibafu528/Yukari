package shibafu.yukari.filter.expression

/**
 * 自身の左右に式を持ち、それらを評価します。
 *
 * Created by shibafu on 15/06/07.
 */
public interface OperatorExpression : Expression {
    public val leftExpression: Expression
    public val rightExpression: Expression
}