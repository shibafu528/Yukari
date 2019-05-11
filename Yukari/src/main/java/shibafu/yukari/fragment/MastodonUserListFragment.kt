package shibafu.yukari.fragment

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.sys1yagi.mastodon4j.MastodonClient
import com.sys1yagi.mastodon4j.api.Range
import com.sys1yagi.mastodon4j.api.exception.Mastodon4jRequestException
import com.sys1yagi.mastodon4j.api.method.Accounts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import shibafu.yukari.fragment.base.AbstractUserListFragment
import shibafu.yukari.mastodon.entity.DonUser
import shibafu.yukari.twitter.AuthUserRecord
import shibafu.yukari.util.getTwitterServiceAwait
import shibafu.yukari.util.showToast
import java.io.IOException


class MastodonUserListFragment : AbstractUserListFragment<DonUser, MastodonUserListFragment.PageCursor>() {
    private var mode: Int = -1
    private var targetUserId: Long = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val arguments = arguments ?: Bundle.EMPTY
        mode = arguments.getInt(EXTRA_MODE, -1)
        targetUserId = arguments.getLong(EXTRA_TARGET_USER_ID, -1)
    }

    override suspend fun takeNextPageAsync(cursor: PageCursor?) = async(Dispatchers.IO) {
        try {
            val service = getTwitterServiceAwait() ?: return@async null

            val api = service.getProviderApi(currentUser) ?: return@async null
            val client = api.getApiClient(currentUser) as? MastodonClient ?: return@async null

            val accounts = Accounts(client)

            val currentRange = cursor?.range ?: Range()
            val pageable = when (mode) {
                MODE_FOLLOWING -> accounts.getFollowing(targetUserId, range = currentRange).execute()
                MODE_FOLLOWER -> accounts.getFollowers(targetUserId, range = currentRange).execute()
                else -> return@async null
            }

            if (pageable.part.isEmpty()) {
                return@async emptyList<DonUser>() to null
            }

            val nextCursor = if (pageable.link?.nextPath.isNullOrEmpty()) null else PageCursor(pageable.nextRange())

            pageable.part.map { DonUser(it) } to nextCursor
        } catch (e: Mastodon4jRequestException) {
            e.printStackTrace()
            Handler(Looper.getMainLooper()).post {
                val response = e.response
                if (response != null) {
                    try {
                        val responseBody = response.body()?.string()
                        showToast("通信エラー: ${response.code()}\n$responseBody")
                    } catch (e: IOException) {
                        showToast("通信エラー: ${response.code()}\nUnknown error")
                    }
                }
                showToast("通信エラー\nUnknown error")
            }
            null
        }
    }

    data class PageCursor(val range: Range) : AbstractUserListFragment.PageCursor

    companion object {
        private const val EXTRA_MODE = "mode"
        private const val EXTRA_TARGET_USER_ID = "targetUserId"

        private const val MODE_FOLLOWING = 0
        private const val MODE_FOLLOWER = 1

        fun newFollowingListInstance(user: AuthUserRecord, title: String, targetUserId: Long): MastodonUserListFragment {
            return MastodonUserListFragment().apply {
                arguments = createBaseArguments(user, title).apply {
                    putInt(EXTRA_MODE, MODE_FOLLOWING)
                    putLong(EXTRA_TARGET_USER_ID, targetUserId)
                }
            }
        }

        fun newFollowerListInstance(user: AuthUserRecord, title: String, targetUserId: Long): MastodonUserListFragment {
            return MastodonUserListFragment().apply {
                arguments = createBaseArguments(user, title).apply {
                    putInt(EXTRA_MODE, MODE_FOLLOWER)
                    putLong(EXTRA_TARGET_USER_ID, targetUserId)
                }
            }
        }
    }
}