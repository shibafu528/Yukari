package shibafu.yukari.filter.expression

import shibafu.yukari.twitter.AuthUserRecord
import twitter4j.TwitterResponse

/**
 * Created by shibafu on 15/06/07.
 */
public class Constant : Value {
    override var value: Any?

    constructor(const: Any) {
        value = const
    }

    override fun evaluate(status: TwitterResponse, userRecords: List<AuthUserRecord>): Boolean
        = if (value is Boolean) value as Boolean else value != null
}