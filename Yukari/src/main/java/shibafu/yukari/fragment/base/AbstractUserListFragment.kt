package shibafu.yukari.fragment.base

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import shibafu.yukari.R
import shibafu.yukari.activity.MainActivity
import shibafu.yukari.activity.ProfileActivity
import shibafu.yukari.common.FontAsset
import shibafu.yukari.common.bitmapcache.ImageLoaderTask
import shibafu.yukari.entity.User
import shibafu.yukari.twitter.AuthUserRecord
import kotlin.coroutines.CoroutineContext

/**
 * [shibafu.yukari.entity.User] の一覧を問い合わせて、画面にリストアップするためのFragment
 */
abstract class AbstractUserListFragment<T : User, PC : AbstractUserListFragment.PageCursor> : ListYukariBaseFragment(), CoroutineScope {
    var title: String = ""

    protected lateinit var currentUser: AuthUserRecord

    private lateinit var footerView: View
    private lateinit var footerProgress: ProgressBar
    private lateinit var footerText: TextView

    private val elements = arrayListOf<T>()
    private var pageCursor: PC? = null
    private var isFooterRemoved: Boolean = false

    private val job = Job()

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    init {
        retainInstance = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val arguments = arguments ?: Bundle.EMPTY
        title = arguments.getString(EXTRA_TITLE, "")
        currentUser = arguments.getSerializable(EXTRA_USER) as AuthUserRecord
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        listAdapter = UserAdapter(requireContext(), elements)

        val footerView = LayoutInflater.from(requireContext()).inflate(R.layout.row_loading, null)
        if (!isFooterRemoved) {
            listView?.addFooterView(footerView)
        }

        this.footerView = footerView
        footerProgress = footerView.findViewById(R.id.pbLoading)
        footerText = footerView.findViewById(R.id.tvLoading)
    }

    override fun onResume() {
        super.onResume()

        val activity = activity
        when (activity) {
            is MainActivity -> { /* do nothing */ }
            is AppCompatActivity -> activity.supportActionBar?.title = title
            else -> activity?.title = title
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    override fun onListItemClick(l: ListView?, v: View?, position: Int, id: Long) {
        if (position < elements.size) {
            // プロフィールを開く
            val user = elements[position]
            startActivity(ProfileActivity.newIntent(requireContext(), currentUser, Uri.parse(user.url)))
        } else {
            // フッタークリック
            takeNextPage(pageCursor)
        }
    }

    override fun onServiceConnected() {
        if (elements.isEmpty()) {
            takeNextPage(null)
        }
    }

    protected abstract suspend fun takeNextPageAsync(cursor: PC?): Deferred<Pair<List<T>, PC?>?>

    private fun takeNextPage(cursor: PC?) {
        launch {
            changeFooterProgress(true)

            val result = takeNextPageAsync(cursor).await()

            if (result != null) {
                val (users, nextCursor) = result

                elements += users
                (listAdapter as? UserAdapter)?.notifyDataSetChanged()

                pageCursor = nextCursor

                changeFooterProgress(false)

                if (nextCursor == null) {
                    // remove footer
                    try {
                        isFooterRemoved = true
                        listView?.removeFooterView(footerView)
                    } catch (e: IllegalStateException) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    private fun changeFooterProgress(isLoading: Boolean) {
        if (isLoading) {
            footerProgress.visibility = View.VISIBLE
            footerText.text = "loading"
        } else {
            footerProgress.visibility = View.INVISIBLE
            footerText.text = "more"
        }
    }

    protected class UserAdapter(context: Context, objects: MutableList<out User>) : ArrayAdapter<User>(context, 0, objects) {
        private val inflater = LayoutInflater.from(context)

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: inflater.inflate(R.layout.row_user, parent, false)
            val vh = view.tag as? ViewHolder ?: ViewHolder.from(view).also { view.tag = it }

            val user = getItem(position)
            if (user != null) {
                vh.tvName.text = user.name
                vh.tvScreenName.text = "@" + user.screenName
                ImageLoaderTask.loadProfileIcon(context.applicationContext, vh.ivIcon, user.biggerProfileImageUrl)
            }

            return view
        }
    }

    data class ViewHolder(val ivIcon: ImageView,
                          val tvScreenName: TextView,
                          val tvName: TextView) {
        companion object {
            fun from(view: View): ViewHolder =
                    ViewHolder(view.findViewById(R.id.user_icon),
                            view.findViewById(R.id.user_sn),
                            view.findViewById(R.id.user_name)).apply {
                        tvScreenName.typeface = FontAsset.getInstance(view.context).font
                        tvName.typeface = FontAsset.getInstance(view.context).font
                    }
        }
    }

    interface PageCursor

    companion object {
        private const val EXTRA_USER = "user"
        private const val EXTRA_TITLE = "title"

        fun createBaseArguments(user: AuthUserRecord, title: String): Bundle {
            return Bundle().apply {
                putSerializable(EXTRA_USER, user)
                putString(EXTRA_TITLE, title)
            }
        }
    }
}