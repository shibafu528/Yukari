package shibafu.yukari.filter.expression

import shibafu.yukari.twitter.AuthUserRecord
import twitter4j.TwitterResponse

/**
 * クエリ式の抽象クラスです。何かを条件に評価することができます。
 *
 * Created by shibafu on 15/06/07.
 */
public interface Expression {
    fun evaluate(status: TwitterResponse, userRecords: List<AuthUserRecord>): Boolean
}