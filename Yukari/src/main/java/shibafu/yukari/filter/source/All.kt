package shibafu.yukari.filter.source

import shibafu.yukari.twitter.AuthUserRecord

/**
 * 全ての受信ツイートを対象とする抽出ソースです。
 *
 * Created by shibafu on 15/06/07.
 */
public data class All(private val pseudo: Unit = Unit) : FilterSource {
    override val sourceAccount: AuthUserRecord? = null

    override fun getRestQuery() = null
}