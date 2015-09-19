package shibafu.yukari.filter.source

import android.content.Context
import shibafu.yukari.twitter.AuthUserRecord
import shibafu.yukari.twitter.streaming.FilterStream

/**
 * 全ての受信ツイートを対象とする抽出ソースです。
 *
 * Created by shibafu on 15/06/07.
 */
public class All : FilterSource {
    override val sourceAccount: AuthUserRecord? = null

    override fun getRestQuery() = null

    override fun requireUserStream(): Boolean = true

    override fun getFilterStream(context: Context): FilterStream? = null
}