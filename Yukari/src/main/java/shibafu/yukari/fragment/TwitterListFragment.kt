package shibafu.yukari.fragment

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import shibafu.yukari.R
import shibafu.yukari.activity.ProfileActivity
import shibafu.yukari.common.bitmapcache.ImageLoaderTask
import shibafu.yukari.fragment.base.AbstractPaginateListFragment
import shibafu.yukari.fragment.tabcontent.TwitterListTimelineFragment
import shibafu.yukari.twitter.AuthUserRecord
import shibafu.yukari.util.getTwitterServiceAwait
import shibafu.yukari.util.showToast
import twitter4j.Twitter
import twitter4j.TwitterException
import twitter4j.UserList

/**
 * TwitterのLists ([UserList]) を一覧するためのListFragment
 */
class TwitterListFragment : AbstractPaginateListFragment<UserList, TwitterListFragment.PageCursor>() {
    private var mode: Int = -1
    private var targetUserId: Long = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val arguments = arguments ?: Bundle.EMPTY
        mode = arguments.getInt(EXTRA_MODE, -1)
        targetUserId = arguments.getLong(EXTRA_TARGET_USER_ID, -1)
    }

    override fun createListAdapter(): ArrayAdapter<UserList> = UserListAdapter(requireContext(), elements)

    override fun onListItemClick(position: Int, item: UserList) {
        val fragment = TwitterListTimelineFragment()

        fragment.arguments = Bundle().apply {
            putSerializable(TwitterListTimelineFragment.EXTRA_USER, currentUser)
            putString(TwitterListTimelineFragment.EXTRA_TITLE, String.format("List: @%s/%s", item.user.screenName, item.name))
            putLong(TwitterListTimelineFragment.EXTRA_LIST_ID, item.id)
        }

        if (activity is ProfileActivity) {
            requireFragmentManager().beginTransaction()
                    .replace(R.id.frame, fragment, ProfileActivity.FRAGMENT_TAG_CONTENT)
                    .addToBackStack(null)
                    .commit()
        }
    }

    override suspend fun takeNextPageAsync(cursor: PageCursor?) = async(Dispatchers.IO) {
        try {
            val service = getTwitterServiceAwait() ?: return@async null

            val api = service.getProviderApi(currentUser) ?: return@async null
            val twitter = api.getApiClient(currentUser) as? Twitter ?: return@async null

            when (mode) {
                MODE_FOLLOWING -> {
                    val response = twitter.getUserLists(targetUserId)
                    response to null
                }
                MODE_MEMBERSHIP -> {
                    val response = twitter.getUserListMemberships(targetUserId, cursor?.cursor ?: -1)
                    val nextCursor = if (response.nextCursor > 0) {
                        PageCursor(response.nextCursor)
                    } else {
                        null
                    }
                    response to nextCursor
                }
                else -> null
            }
        } catch (e: TwitterException) {
            e.printStackTrace()
            Handler(Looper.getMainLooper()).post {
                showToast("通信エラー: ${e.statusCode}:${e.errorCode}\n${e.errorMessage}")
            }
            return@async null
        }
    }

    data class PageCursor(val cursor: Long) : AbstractPaginateListFragment.PageCursor

    class UserListAdapter(context: Context, objects: List<UserList>) : ArrayAdapter<UserList>(context, 0, objects) {
        private val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: inflater.inflate(R.layout.row_list, parent, false)
            val vh = view.tag as? ViewHolder ?: ViewHolder.from(view).also { view.tag = it }

            val list = getItem(position)
            if (list != null) {
                vh.tvTitle.text = "@${list.user.screenName}/${list.name}"
                vh.tvDescription.text = list.description
                vh.tvMembers.text = buildString {
                    append("${list.memberCount} 人のメンバー")
                    if (list.isPublic && list.subscriberCount > 0) {
                        append(", ${list.subscriberCount} 人がリストを保存")
                    }
                }
                ImageLoaderTask.loadProfileIcon(context.applicationContext, vh.ivIcon, list.user.biggerProfileImageURLHttps)
            }

            return view
        }
    }

    data class ViewHolder(val ivIcon: ImageView,
                          val tvTitle: TextView,
                          val tvDescription: TextView,
                          val tvMembers: TextView) {
        companion object {
            fun from(view: View): ViewHolder =
                    ViewHolder(view.findViewById(R.id.list_icon),
                            view.findViewById(R.id.list_name),
                            view.findViewById(R.id.list_desc),
                            view.findViewById(R.id.list_members))
        }
    }

    companion object {
        private const val MODE_FOLLOWING = 0
        private const val MODE_MEMBERSHIP = 1

        private const val EXTRA_MODE = "mode"
        private const val EXTRA_TARGET_USER_ID = "targetUserId"

        @JvmStatic
        fun newFollowingListInstance(user: AuthUserRecord, title: String, targetUserId: Long): TwitterListFragment {
            return TwitterListFragment().apply {
                arguments = createBaseArguments(user, title).apply {
                    putInt(EXTRA_MODE, MODE_FOLLOWING)
                    putLong(EXTRA_TARGET_USER_ID, targetUserId)
                }
            }
        }

        @JvmStatic
        fun newMembershipListInstance(user: AuthUserRecord, title: String, targetUserId: Long): TwitterListFragment {
            return TwitterListFragment().apply {
                arguments = createBaseArguments(user, title).apply {
                    putInt(EXTRA_MODE, MODE_MEMBERSHIP)
                    putLong(EXTRA_TARGET_USER_ID, targetUserId)
                }
            }
        }
    }
}