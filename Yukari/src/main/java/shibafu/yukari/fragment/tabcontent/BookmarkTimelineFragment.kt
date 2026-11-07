package shibafu.yukari.fragment.tabcontent

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import shibafu.yukari.core.App
import shibafu.yukari.database.Bookmark
import shibafu.yukari.database.MuteConfig
import shibafu.yukari.entity.Status
import shibafu.yukari.util.getTwitterServiceAwait
import kotlin.coroutines.CoroutineContext

/**
 * ブックマーク表示専用のタイムライン
 */
class BookmarkTimelineFragment : TimelineFragment(), CoroutineScope {
    private val job = Job()

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    override fun onResume() {
        super.onResume()
        if (isTwitterServiceBound && statuses.isNotEmpty()) {
            loadBookmark()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        loadBookmark()
    }

    override fun onRefresh() {
        super.onRefresh()
        loadBookmark()
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    private fun loadBookmark() = launch {
        swipeRefreshLayout?.isRefreshing = true

        val bookmarks = async(Dispatchers.IO) {
            val service = getTwitterServiceAwait() ?: return@async emptyList<Bookmark>()
            service.database.bookmarks
        }.await()

        // はい、調子乗ってブクマ見るやろ お前ほんまに覚えとけよ ガチで消したるからな
        // ほんまにキレタ 絶対許さん お前のID控えたからな
        val shownIds = statuses.map(Status::id).toMutableList()
        val mutedIds = mutedStatuses.map(Status::id).toMutableList()

        val suppressor = twitterService.suppressor

        var mute: BooleanArray
        bookmarks.forEach { status ->
            status.setRepresentIfOwned(twitterService.users)
            if (!status.isOwnedStatus()) {
                // 優先アカウント設定が存在するか？
                val priorityAccount = App.getInstance(requireContext()).userExtrasManager.userExtras.firstOrNull { it.id == status.originStatus.user.identicalUrl }?.priorityAccount
                if (priorityAccount != null) {
                    status.prioritizedUser = priorityAccount
                }
            }

            mute = suppressor.decision(status)
            if (mute[MuteConfig.MUTE_IMAGE_THUMB]) {
                status.metadata.isCensoredThumbs = true
            }
            val isMuted = mute[MuteConfig.MUTE_TWEET_RTED] ||
                    (!status.isRepost && mute[MuteConfig.MUTE_TWEET]) ||
                    (status.isRepost && mute[MuteConfig.MUTE_RETWEET])

            if (isMuted) {
                mutedStatuses += status

                if (mutedIds.contains(status.id)) {
                    mutedIds.remove(status.id)
                }
            } else {
                insertElement(status, false)

                if (shownIds.contains(status.id)) {
                    shownIds.remove(status.id)
                }
            }
        }

        val removeStatuses = arrayListOf<Status>()
        // ConcurrentModify対策で別のコレクションに移す
        statuses.forEach { status ->
            if (shownIds.contains(status.id)) {
                removeStatuses += status
            }
        }
        removeStatuses.forEach { status ->
            deleteElement(status.providerHost, status.id)
        }

        val iterator = mutedStatuses.listIterator()
        while (iterator.hasNext()) {
            if (mutedIds.contains(iterator.next().id)) {
                iterator.remove()
            }
        }

        swipeRefreshLayout?.isRefreshing = false
    }
}