package shibafu.yukari.filter.expression

import shibafu.yukari.twitter.AuthUserRecord
import twitter4j.TwitterResponse

/**
 * 値を表現します。
 *
 * Created by shibafu on 15/06/07.
 */
public interface Value : Expression {
    var value: Any?
        private set
}