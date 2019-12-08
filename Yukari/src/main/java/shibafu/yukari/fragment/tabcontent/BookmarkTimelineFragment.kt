package shibafu.yukari.fragment.tabcontent

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import shibafu.yukari.database.Bookmark
import shibafu.yukari.database.MuteConfig
import shibafu.yukari.entity.Status
import shibafu.yukari.twitter.TwitterUtil
import shibafu.yukari.twitter.entity.TwitterStatus
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
        val userExtras = twitterService.userExtras

        var mute: BooleanArray
        bookmarks.forEach { status ->
            val checkOwn = twitterService.isMyTweet(status)
            if (checkOwn != null) {
                status.setOwner(checkOwn)
            } else {
                val url = TwitterUtil.getUrlFromUserId(status.sourceUser.id)
                val first = userExtras.firstOrNull { it.id == url }
                if (first != null && first.priorityAccount != null) {
                    status.setOwner(first.priorityAccount)
                }
            }

            mute = suppressor.decision(status)
            if (mute[MuteConfig.MUTE_IMAGE_THUMB]) {
                status.isCensoredThumbs = true
            }
            val isMuted = mute[MuteConfig.MUTE_TWEET_RTED] ||
                    (!status.isRetweet && mute[MuteConfig.MUTE_TWEET]) ||
                    (status.isRetweet && mute[MuteConfig.MUTE_RETWEET])

            val twitterStatus = TwitterStatus(status, status.representUser)

            if (isMuted) {
                mutedStatuses += twitterStatus

                if (mutedIds.contains(status.id)) {
                    mutedIds.remove(status.id)
                }
            } else {
                insertElement(twitterStatus, false)

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