package shibafu.yukari.filter.expression

import shibafu.yukari.twitter.AuthUserRecord
import twitter4j.TwitterResponse

/**
 * Created by shibafu on 15/06/07.
 */
public class ParameterValue(val path: String) : ValueExpression {
    override var value: Any? = null

    override fun evaluate(status: TwitterResponse, userRecords: List<AuthUserRecord>): Boolean {
        fun invoke(pathList: List<String>, target: Any?) {
            if (target == null) return

            val method = target.javaClass.methods.first { it.name.toLowerCase().equals("get" + pathList.first().toLowerCase()) }

            if (pathList.size() == 1) method.invoke(target)
            else invoke(pathList.drop(1), method.invoke(target))
        }
        val pathList = path.split('.').toArrayList()
        value = if (path.startsWith('@')) {
            val screenName = pathList.first().substring(1)
            userRecords.firstOrNull{ it.ScreenName.equals(screenName) }.let { invoke(pathList.drop(1), it) }
        } else {
            invoke(pathList, status)
        }
        return if (value is Boolean) value as Boolean else value != null
    }
}