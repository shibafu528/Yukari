package shibafu.yukari.fragment

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ContextMenu
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import shibafu.yukari.R
import shibafu.yukari.activity.AccountChooserActivity
import shibafu.yukari.activity.ProfileActivity
import shibafu.yukari.common.bitmapcache.ImageLoaderTask
import shibafu.yukari.database.Provider
import shibafu.yukari.fragment.base.AbstractPaginateListFragment
import shibafu.yukari.fragment.tabcontent.TwitterListTimelineFragment
import shibafu.yukari.database.AuthUserRecord
import shibafu.yukari.util.getTwitterServiceAwait
import shibafu.yukari.util.showToast
import twitter4j.Twitter
import twitter4j.TwitterException
import twitter4j.UserList

/**
 * TwitterのLists ([UserList]) を一覧するためのListFragment
 */
class TwitterListFragment : AbstractPaginateListFragment<UserList, TwitterListFragment.PageCursor>(), SimpleAlertDialogFragment.OnDialogChoseListener {
    private var mode: Int = -1
    private var targetUserId: Long = -1

    private lateinit var addMenu: MenuItem

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val arguments = arguments ?: Bundle.EMPTY
        mode = arguments.getInt(EXTRA_MODE, -1)
        targetUserId = arguments.getLong(EXTRA_TARGET_USER_ID, -1)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setHasOptionsMenu(true)
        registerForContextMenu(listView)
    }

    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {
        if (menu != null) {
            addMenu = menu.add(Menu.NONE, R.id.action_add, Menu.NONE, "新規作成")
                    .setIcon(R.drawable.ic_action_add)
                    .setVisible(false)
            addMenu.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.action_add -> {
                val fragment = UserListEditDialogFragment.newInstance(currentUser, DIALOG_REQUEST_CREATE)
                fragment.setTargetFragment(this, DIALOG_REQUEST_CREATE)
                fragment.show(requireFragmentManager(), "new")
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onCreateContextMenu(menu: ContextMenu?, v: View?, menuInfo: ContextMenu.ContextMenuInfo?) {
        super.onCreateContextMenu(menu, v, menuInfo)

        if (menu == null) return

        val adapterMenuInfo = menuInfo as? AdapterView.AdapterContextMenuInfo ?: return
        val listView = v as? ListView ?: return
        val userList = listView.getItemAtPosition(adapterMenuInfo.position) as? UserList ?: return

        requireActivity().menuInflater.inflate(R.menu.list, menu)
        menu.setHeaderTitle("@${userList.user.screenName}/${userList.name}")

        if (userList.user.id == currentUser.NumericId) {
            menu.findItem(R.id.action_edit).isVisible = true
            menu.findItem(R.id.action_delete).isVisible = true
        } else {
            menu.findItem(R.id.action_unsubscribe).isVisible = true
            menu.findItem(R.id.action_subscribe).isVisible = true
        }
    }

    override fun onContextItemSelected(item: MenuItem?): Boolean {
        if (item == null) return super.onContextItemSelected(item)

        val adapterMenuInfo = item.menuInfo as AdapterView.AdapterContextMenuInfo
        val userList = listView.getItemAtPosition(adapterMenuInfo.position) as UserList

        when (item.itemId) {
            R.id.action_show_member -> {
                val fragment = TwitterUserListFragment.newListMembersInstance(currentUser, "Member: ${userList.fullName}", userList.id)
                val activity = activity
                if (activity is ProfileActivity) {
                    requireFragmentManager().beginTransaction()
                            .replace(R.id.frame, fragment, ProfileActivity.FRAGMENT_TAG_CONTENT)
                            .addToBackStack(null)
                            .commit()
                }
                return true
            }
            R.id.action_show_subscriber -> {
                val fragment = TwitterUserListFragment.newListSubscribersInstance(currentUser, "Subscriber: ${userList.fullName}", userList.id)
                val activity = activity
                if (activity is ProfileActivity) {
                    requireFragmentManager().beginTransaction()
                            .replace(R.id.frame, fragment, ProfileActivity.FRAGMENT_TAG_CONTENT)
                            .addToBackStack(null)
                            .commit()
                }
                return true
            }
            R.id.action_subscribe -> {
                val intent = Intent(requireActivity(), AccountChooserActivity::class.java)
                intent.putExtra(AccountChooserActivity.EXTRA_METADATA, userList.id.toString())
                intent.putExtra(AccountChooserActivity.EXTRA_FILTER_PROVIDER_API_TYPE, Provider.API_TWITTER)
                startActivityForResult(intent, REQUEST_SUBSCRIBE)
                return true
            }
            R.id.action_unsubscribe -> {
                val intent = Intent(requireActivity(), AccountChooserActivity::class.java)
                intent.putExtra(AccountChooserActivity.EXTRA_METADATA, userList.id.toString())
                intent.putExtra(AccountChooserActivity.EXTRA_FILTER_PROVIDER_API_TYPE, Provider.API_TWITTER)
                startActivityForResult(intent, REQUEST_UNSUBSCRIBE)
                return true
            }
            R.id.action_edit -> {
                val fragment = UserListEditDialogFragment.newInstance(currentUser, userList, DIALOG_REQUEST_EDIT)
                fragment.setTargetFragment(this, DIALOG_REQUEST_EDIT)
                fragment.show(requireFragmentManager(), "edit")
                return true
            }
            R.id.action_delete -> {
                val fragment = SimpleAlertDialogFragment.Builder(DIALOG_REQUEST_DELETE)
                        .setTitle("確認")
                        .setMessage("リストを削除してもよろしいですか？")
                        .setPositive("OK")
                        .setNegative("キャンセル")
                        .setExtras(Bundle().apply {
                            putSerializable("userList", userList)
                        })
                        .build()
                fragment.setTargetFragment(this, DIALOG_REQUEST_DELETE)
                fragment.show(requireFragmentManager(), "delete")
                return true
            }
        }

        return super.onContextItemSelected(item)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode != Activity.RESULT_OK) return

        when (requestCode) {
            REQUEST_SUBSCRIBE -> {
                val userListId = data?.getStringExtra(AccountChooserActivity.EXTRA_METADATA)?.toLong() ?: return

                launch {
                    val result = twitterAsync { twitter ->
                        try {
                            twitter.createUserListSubscription(userListId)

                            true
                        } catch (e: TwitterException) {
                            e.printStackTrace()
                            Handler(Looper.getMainLooper()).post {
                                showToast("通信エラー: ${e.statusCode}:${e.errorCode}\n${e.errorMessage}")
                            }

                            null
                        }
                    }.await()

                    if (result == true) {
                        showToast("リストを保存しました")
                    }
                }
            }
            REQUEST_UNSUBSCRIBE -> {
                val userListId = data?.getStringExtra(AccountChooserActivity.EXTRA_METADATA)?.toLong() ?: return

                launch {
                    val result = twitterAsync { twitter ->
                        try {
                            twitter.destroyUserListSubscription(userListId)

                            true
                        } catch (e: TwitterException) {
                            e.printStackTrace()
                            Handler(Looper.getMainLooper()).post {
                                showToast("通信エラー: ${e.statusCode}:${e.errorCode}\n${e.errorMessage}")
                            }

                            null
                        }
                    }.await()

                    if (result == true) {
                        showToast("リストの保存を解除しました")

                        if (targetUserId == data.getLongExtra(AccountChooserActivity.EXTRA_SELECTED_USERID, -1) && mode == MODE_FOLLOWING) {
                            val iterator = elements.iterator()
                            while (iterator.hasNext()) {
                                val id = iterator.next().id
                                if (id == userListId) {
                                    iterator.remove()
                                    (listAdapter as ArrayAdapter<*>).notifyDataSetChanged()
                                    break
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (mode == MODE_FOLLOWING && targetUserId == currentUser.NumericId) {
            addMenu.isVisible = true
        }
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

    override suspend fun takeNextPageAsync(cursor: PageCursor?) = twitterAsync { twitter ->
        try {
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

            null
        }
    }

    override fun onDialogChose(requestCode: Int, which: Int, extras: Bundle?) {
        when (requestCode) {
            DIALOG_REQUEST_CREATE, DIALOG_REQUEST_EDIT -> {
                if (which == DialogInterface.BUTTON_POSITIVE) {
                    refresh()
                }
            }
            DIALOG_REQUEST_DELETE -> {
                if (which == DialogInterface.BUTTON_POSITIVE) {
                    launch {
                        val userList = extras?.getSerializable("userList") as? UserList ?: return@launch

                        val result = twitterAsync { twitter ->
                            try {
                                twitter.destroyUserList(userList.id)

                                true
                            } catch (e: TwitterException) {
                                e.printStackTrace()
                                Handler(Looper.getMainLooper()).post {
                                    showToast("通信エラー: ${e.statusCode}:${e.errorCode}\n${e.errorMessage}")
                                }

                                null
                            }
                        }.await()

                        if (result == true) {
                            showToast("削除しました")
                            refresh()
                        }
                    }
                }
            }
        }
    }

    private fun <T> twitterAsync(block: suspend CoroutineScope.(twitter: Twitter) -> T?): Deferred<T?> {
        return async(Dispatchers.IO) {
            val service = getTwitterServiceAwait() ?: return@async null

            val api = service.getProviderApi(currentUser) ?: return@async null
            val twitter = api.getApiClient(currentUser) as? Twitter ?: return@async null

            block(twitter)
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

        private const val REQUEST_SUBSCRIBE = 1
        private const val REQUEST_UNSUBSCRIBE = 2

        private const val DIALOG_REQUEST_CREATE = 1
        private const val DIALOG_REQUEST_EDIT = 2
        private const val DIALOG_REQUEST_DELETE = 3

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