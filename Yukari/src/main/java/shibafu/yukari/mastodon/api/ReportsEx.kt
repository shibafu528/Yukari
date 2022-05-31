package shibafu.yukari.mastodon.api

import com.sys1yagi.mastodon4j.MastodonClient
import com.sys1yagi.mastodon4j.MastodonRequest
import com.sys1yagi.mastodon4j.Parameter
import com.sys1yagi.mastodon4j.api.entity.Report
import com.sys1yagi.mastodon4j.api.exception.Mastodon4jRequestException
import okhttp3.MediaType
import okhttp3.RequestBody

class ReportsEx(private val client: MastodonClient) {
    /**
     * POST /api/v1/reports
     *
     * [Document](https://docs.joinmastodon.org/methods/accounts/reports/)
     */
    @Throws(Mastodon4jRequestException::class)
    fun portReports(accountId: Long,
                    statusIds: List<Long>? = emptyList(),
                    comment: String? = null,
                    forward: Boolean = false,
                    category: Category? = null,
                    ruleIds: List<String>? = emptyList()): MastodonRequest<Report> {
        val params = Parameter().apply {
            append("account_id", accountId)
            if (!statusIds.isNullOrEmpty()) {
                append("status_ids", statusIds)
            }
            if (!comment.isNullOrEmpty()) {
                append("comment", comment)
            }
            if (forward) {
                append("forward", "true")
            }
            if (category != null) {
                append("category", category.toString())
            }
            if (!ruleIds.isNullOrEmpty()) {
                append("rule_ids", ruleIds)
            }
        }.build()
        return MastodonRequest(
                {
                    client.post("reports", RequestBody.create(MediaType.parse("application/x-www-form-urlencoded; charset=utf-8"), params))
                },
                {
                    client.getSerializer().fromJson(it, Report::class.java)
                }
        )
    }

    enum class Category(private val value: String) {
        /**
         * other reason. (this is default)
         */
        OTHER("other"),

        /**
         * this account/post is spam.
         */
        SPAM("spam"),

        /**
         * this account/post violate server rules.
         */
        VIOLATION("violation"),
        ;

        override fun toString() = value

        companion object {
            private val valueMap = values().associateBy { it.value }

            fun of(value: String) = valueMap[value] ?: throw IllegalArgumentException()
        }
    }
}
