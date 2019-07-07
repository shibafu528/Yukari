package shibafu.yukari.fragment.tabcontent

import android.os.Bundle
import android.preference.PreferenceManager
import android.view.View
import android.widget.AbsListView
import android.widget.TextView
import org.eclipse.collections.impl.set.mutable.primitive.LongHashSet
import shibafu.yukari.R
import shibafu.yukari.entity.Status

/**
 * 未読ビューの振る舞い制御
 */
internal class UnreadNotifierBehavior(private val parent: TimelineFragment,
                                      private val statuses: MutableList<Status>,
                                      private val unreadSet: LongHashSet) : AbsListView.OnScrollListener {
    private var lastShowedFirstItemId: Long = -1
    private var lastShowedFirstItemY = 0
    private var unreadNotifierView: View? = null
    private var tvUnreadCount: TextView? = null

    fun onCreateView(parentView: View) {
        unreadNotifierView = parentView.findViewById(R.id.unreadNotifier)
        tvUnreadCount = unreadNotifierView?.findViewById(R.id.textView) as TextView
    }

    fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        val unreadNotifier = unreadNotifierView
        if (unreadNotifier != null) {
            if (PreferenceManager.getDefaultSharedPreferences(parent.activity).getString("pref_theme", "light").endsWith("dark")) {
                unreadNotifier.setBackgroundResource(R.drawable.dialog_full_material_dark)
            } else {
                unreadNotifier.setBackgroundResource(R.drawable.dialog_full_material_light)
            }

            unreadNotifier.setOnClickListener { _ -> parent.scrollToOldestUnread() }
        }
    }

    fun onStart() {
        if (lastShowedFirstItemId > -1) {
            for (position in 0 until statuses.size) {
                if (statuses[position].id == lastShowedFirstItemId) {
                    parent.listView.setSelectionFromTop(position, lastShowedFirstItemY)
                    break
                }
            }
            updateUnreadNotifier()
        }
    }

    fun onStop() {
        lastShowedFirstItemId = parent.listView.getItemIdAtPosition(parent.listView.firstVisiblePosition)
        lastShowedFirstItemY = parent.listView.getChildAt(0)?.top ?: 0
    }

    fun onDetach() {
        unreadNotifierView = null
        tvUnreadCount = null
    }

    fun updateUnreadNotifier() {
        val unreadNotifier = unreadNotifierView ?: return

        if (unreadSet.size() < 1) {
            unreadNotifier.visibility = View.INVISIBLE
            return
        }
        tvUnreadCount?.text = "新着 ${unreadSet.size()}件"

        unreadNotifier.visibility = View.VISIBLE
    }

    fun clearUnreadNotifier() {
        unreadSet.clear()
        updateUnreadNotifier()
    }

    override fun onScrollStateChanged(view: AbsListView?, scrollState: Int) {}

    override fun onScroll(view: AbsListView?, firstVisibleItem: Int, visibleItemCount: Int, totalItemCount: Int) {
        var i = firstVisibleItem
        while (i < i + visibleItemCount && i < statuses.size) {
            val element = statuses[firstVisibleItem]
            if (unreadSet.contains(element.id)) {
                unreadSet.remove(element.id)
            }
            ++i
        }
        updateUnreadNotifier()
    }
}