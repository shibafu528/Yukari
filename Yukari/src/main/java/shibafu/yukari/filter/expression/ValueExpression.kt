package shibafu.yukari.filter.expression

/**
 * 値を表現します。
 *
 * Created by shibafu on 15/06/07.
 */
public interface ValueExpression : Expression {
    public var value: Any?
        private set
}