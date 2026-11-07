package shibafu.yukari.fragment

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import shibafu.yukari.core.App
import shibafu.yukari.fragment.base.AbstractUserListFragment
import shibafu.yukari.database.AuthUserRecord
import shibafu.yukari.twitter.entity.TwitterUser
import shibafu.yukari.util.getTwitterServiceAwait
import shibafu.yukari.util.showToast
import twitter4j.Twitter
import twitter4j.TwitterException
import twitter4j.User

class TwitterUserListFragment : AbstractUserListFragment<TwitterUser, TwitterUserListFragment.PageCursor>() {

    private var mode: Int = -1
    private var query: String = ""
    private var targetListId: Long = -1
    private var targetUserId: Long = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val arguments = arguments ?: Bundle.EMPTY
        mode = arguments.getInt(EXTRA_MODE, -1)
        query = arguments.getString(EXTRA_SEARCH_QUERY, "")
        targetListId = arguments.getLong(EXTRA_TARGET_LIST_ID, -1)
        targetUserId = (arguments.getSerializable(EXTRA_TARGET_USER) as? User)?.id ?: -1
    }

    override suspend fun takeNextPageAsync(cursor: PageCursor?) = async(Dispatchers.IO) {
        try {
            val api = App.getInstance(requireContext()).getProviderApi(currentUser) ?: return@async null
            val twitter = api.getApiClient(currentUser) as? Twitter ?: return@async null

            var nextCursor = cursor
            if (mode == MODE_SEARCH) {
                val users = twitter.searchUsers(query, cursor?.cursor?.toInt() ?: 1)

                nextCursor = when {
                    users.size < 20 -> null
                    cursor == null -> PageCursor(2)
                    else -> PageCursor(cursor.cursor + 1)
                }

                users.map { TwitterUser(it) } to nextCursor
            } else {
                val currentCursor = cursor?.cursor ?: -1

                val users = when (mode) {
                    MODE_FRIEND -> twitter.getFriendsList(targetUserId, currentCursor)
                    MODE_FOLLOWER -> twitter.getFollowersList(targetUserId, currentCursor)
                    MODE_BLOCKING -> twitter.getBlocksList(currentCursor)
                    MODE_LIST_MEMBER -> twitter.getUserListMembers(targetListId, currentCursor)
                    MODE_LIST_SUBSCRIBER -> twitter.getUserListSubscribers(targetListId, currentCursor)
                    else -> return@async null
                }

                if (users != null && users.isNotEmpty()) {
                    if (users.nextCursor == 0L) {
                        nextCursor = null
                    } else {
                        nextCursor = PageCursor(users.nextCursor)
                    }
                }

                users.map { TwitterUser(it) } to nextCursor
            }
        } catch (e: TwitterException) {
            e.printStackTrace()
            Handler(Looper.getMainLooper()).post {
                showToast("通信エラー: ${e.statusCode}:${e.errorCode}\n${e.errorMessage}")
            }
            return@async null
        }
    }

    data class PageCursor(val cursor: Long) : AbstractUserListFragment.PageCursor

    companion object {
        private const val EXTRA_MODE = "mode"
        private const val EXTRA_TARGET_USER = "targetUser"
        private const val EXTRA_TARGET_LIST_ID = "targetListId"
        private const val EXTRA_SEARCH_QUERY = "searchQuery"

        private const val MODE_FRIEND = 0
        private const val MODE_FOLLOWER = 1
        private const val MODE_BLOCKING = 2
        private const val MODE_SEARCH = 3
        private const val MODE_LIST_MEMBER = 4
        private const val MODE_LIST_SUBSCRIBER = 5

        @JvmStatic
        fun newFriendListInstance(user: AuthUserRecord, title: String, targetUser: User): TwitterUserListFragment {
            return TwitterUserListFragment().apply {
                arguments = createBaseArguments(user, title).apply {
                    putInt(EXTRA_MODE, MODE_FRIEND)
                    putSerializable(EXTRA_TARGET_USER, targetUser)
                }
            }
        }

        @JvmStatic
        fun newFollowerListInstance(user: AuthUserRecord, title: String, targetUser: User): TwitterUserListFragment {
            return TwitterUserListFragment().apply {
                arguments = createBaseArguments(user, title).apply {
                    putInt(EXTRA_MODE, MODE_FOLLOWER)
                    putSerializable(EXTRA_TARGET_USER, targetUser)
                }
            }
        }

        @JvmStatic
        fun newBlockingListInstance(user: AuthUserRecord, title: String, targetUser: User): TwitterUserListFragment {
            return TwitterUserListFragment().apply {
                arguments = createBaseArguments(user, title).apply {
                    putInt(EXTRA_MODE, MODE_BLOCKING)
                    putSerializable(EXTRA_TARGET_USER, targetUser)
                }
            }
        }

        @JvmStatic
        fun newListMembersInstance(user: AuthUserRecord, title: String, targetListId: Long): TwitterUserListFragment {
            return TwitterUserListFragment().apply {
                arguments = createBaseArguments(user, title).apply {
                    putInt(EXTRA_MODE, MODE_LIST_MEMBER)
                    putLong(EXTRA_TARGET_LIST_ID, targetListId)
                }
            }
        }

        @JvmStatic
        fun newListSubscribersInstance(user: AuthUserRecord, title: String, targetListId: Long): TwitterUserListFragment {
            return TwitterUserListFragment().apply {
                arguments = createBaseArguments(user, title).apply {
                    putInt(EXTRA_MODE, MODE_LIST_SUBSCRIBER)
                    putLong(EXTRA_TARGET_LIST_ID, targetListId)
                }
            }
        }

        @JvmStatic
        fun newSearchInstance(user: AuthUserRecord, title: String, query: String): TwitterUserListFragment {
            return TwitterUserListFragment().apply {
                arguments = createBaseArguments(user, title).apply {
                    putInt(EXTRA_MODE, MODE_SEARCH)
                    putString(EXTRA_SEARCH_QUERY, query)
                }
            }
        }
    }
}