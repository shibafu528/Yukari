package shibafu.yukari.mastodon.api

import com.sys1yagi.mastodon4j.MastodonClient
import com.sys1yagi.mastodon4j.MastodonRequest
import com.sys1yagi.mastodon4j.api.entity.Relationship
import com.sys1yagi.mastodon4j.extension.emptyRequestBody

class AccountsEx(private val client: MastodonClient) {
    /**
     * POST /api/v1/accounts/:id/remove_from_followers
     *
     * [Document](https://docs.joinmastodon.org/methods/accounts/#remove_from_followers)
     */
    fun postRemoveFromFollowers(accountId: Long): MastodonRequest<Relationship> {
        return MastodonRequest<Relationship>(
            {
                client.post("accounts/$accountId/remove_from_followers", emptyRequestBody())
            },
            {
                client.getSerializer().fromJson(it, Relationship::class.java)
            }
        )
    }
}