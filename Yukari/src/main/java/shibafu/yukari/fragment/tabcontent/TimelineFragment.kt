package shibafu.yukari.fragment.tabcontent

import android.os.Bundle
import android.support.v4.app.ListFragment
import android.view.View
import android.widget.Toast
import shibafu.yukari.common.TweetAdapter
import shibafu.yukari.twitter.AuthUserRecord

/**
 * 時系列順に要素を並べて表示するタブの基底クラス
 */
open class TimelineFragment : ListFragment(), TimelineTab {
    protected val statuses: MutableList<Any> = arrayListOf() // TODO :thinking_face:
    protected val users: MutableList<AuthUserRecord> = arrayListOf()

    protected var statusAdapter: TweetAdapter? = null

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        statusAdapter = TweetAdapter(context, users, null, statuses)
    }

    override fun onDetach() {
        super.onDetach()
        listAdapter = null
        statusAdapter = null
    }

    override fun isCloseable(): Boolean = false

    override fun scrollToTop() {
        try {
            listView?.setSelection(0)
        } catch (e: IllegalStateException) {
            e.printStackTrace()
            val activity = activity
            if (activity?.applicationContext != null) {
                Toast.makeText(activity.applicationContext, e.localizedMessage, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun scrollToBottom() {
        try {
            val count = listAdapter?.count ?: 1
            listView?.setSelection(count - 1)
        } catch (e: IllegalStateException) {
            e.printStackTrace()
            val activity = activity
            if (activity?.applicationContext != null) {
                Toast.makeText(activity.applicationContext, e.localizedMessage, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun scrollToOldestUnread() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun scrollToPrevPage() {
        try {
            listView?.let {
                it.smoothScrollBy(-it.height, 100)
                it.setSelection(it.firstVisiblePosition)
            }
        } catch (e: IllegalStateException) {
            e.printStackTrace()
            val activity = activity
            if (activity?.applicationContext != null) {
                Toast.makeText(activity.applicationContext, e.localizedMessage, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun scrollToNextPage() {
        try {
            listView?.let {
                it.smoothScrollBy(it.height, 100)
                it.setSelection(it.firstVisiblePosition)
            }
        } catch (e: IllegalStateException) {
            e.printStackTrace()
            val activity = activity
            if (activity?.applicationContext != null) {
                Toast.makeText(activity.applicationContext, e.localizedMessage, Toast.LENGTH_SHORT).show()
            }
        }
    }
}

/*
  TODO
  - AuthUserRecordでTwitter以外のユーザ情報は賄えるのか？
  - TwitterResponseに代わる、何かStatusを表わす型が欲しい
    - StatusViewもそれに対応する必要がある
  - LimitedTLは最早標準でいいと思う
 */