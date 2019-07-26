package shibafu.yukari.fragment.base

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
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
import shibafu.yukari.twitter.AuthUserRecord
import kotlin.coroutines.CoroutineContext

/**
 * ページネーションを備えたListFragment
 */
abstract class AbstractPaginateListFragment<T, PC : AbstractPaginateListFragment.PageCursor> : ListYukariBaseFragment(), CoroutineScope {
    var title: String = ""

    protected lateinit var currentUser: AuthUserRecord

    private lateinit var footerView: View
    private lateinit var footerProgress: ProgressBar
    private lateinit var footerText: TextView

    protected val elements = arrayListOf<T>()
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

        listAdapter = createListAdapter()

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
            // アイテムクリック
            onListItemClick(position, elements[position])
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

    override fun onServiceDisconnected() {}

    protected abstract fun createListAdapter(): ArrayAdapter<T>

    protected abstract fun onListItemClick(position: Int, item: T)

    protected abstract suspend fun takeNextPageAsync(cursor: PC?): Deferred<Pair<List<T>, PC?>?>

    private fun takeNextPage(cursor: PC?) {
        launch {
            changeFooterProgress(true)

            val result = takeNextPageAsync(cursor).await()

            if (result != null) {
                val (items, nextCursor) = result

                elements += items
                (listAdapter as? ArrayAdapter<*>)?.notifyDataSetChanged()

                pageCursor = nextCursor

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

            changeFooterProgress(false)
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