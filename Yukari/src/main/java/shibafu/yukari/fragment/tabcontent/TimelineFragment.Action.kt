package shibafu.yukari.fragment.tabcontent

import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import shibafu.yukari.activity.ProfileActivity
import shibafu.yukari.activity.StatusActivity
import shibafu.yukari.activity.TraceActivity
import shibafu.yukari.activity.TweetActivity
import shibafu.yukari.entity.Status
import shibafu.yukari.fragment.SimpleAlertDialogFragment
import shibafu.yukari.service.AsyncCommandService
import shibafu.yukari.util.defaultSharedPreferences

internal enum class TimelineItemClickAction {
    OPEN_DETAIL {
        override fun onItemClick(): TimelineFragment.(Status) -> Boolean = { clickedElement ->
            val intent = Intent(activity, StatusActivity::class.java)
            intent.putExtra(StatusActivity.EXTRA_STATUS, clickedElement)
            intent.putExtra(StatusActivity.EXTRA_USER, clickedElement.representUser)
            startActivity(intent)
            true
        }
    },
    OPEN_PROFILE {
        override fun onItemClick(): TimelineFragment.(Status) -> Boolean = { clickedElement ->
            val intent = ProfileActivity.newIntent(requireContext(),
                    clickedElement.representUser,
                    Uri.parse(clickedElement.originStatus.user.url))
            startActivity(intent)
            true
        }
    },
    OPEN_THREAD {
        override fun onItemClick(): TimelineFragment.(Status) -> Boolean = { clickedElement ->
            if (clickedElement.originStatus.inReplyToId > -1) {
                val intent = Intent(activity, TraceActivity::class.java)
                intent.putExtra(TweetListFragment.EXTRA_USER, clickedElement.representUser)
                intent.putExtra(TweetListFragment.EXTRA_TITLE, "Trace")
                intent.putExtra(TraceActivity.EXTRA_TRACE_START, clickedElement.originStatus)
                startActivity(intent)
                true
            } else {
                false
            }
        }
    },
    REPLY {
        override fun onItemClick(): TimelineFragment.(Status) -> Boolean = { clickedElement ->
            val intent = Intent(activity, TweetActivity::class.java)
            intent.putExtra(TweetActivity.EXTRA_USER, clickedElement.representUser)
            intent.putExtra(TweetActivity.EXTRA_STATUS, clickedElement.originStatus)
            intent.putExtra(TweetActivity.EXTRA_MODE, TweetActivity.MODE_REPLY)
            intent.putExtra(TweetActivity.EXTRA_TEXT, "@" + clickedElement.originStatus.user.screenName + " ")
            startActivity(intent)
            true
        }
    },
    REPLY_ALL {
        override fun onItemClick(): TimelineFragment.(Status) -> Boolean = { clickedElement ->
            val userRecord = clickedElement.representUser

            val intent = Intent(activity, TweetActivity::class.java)
            intent.putExtra(TweetActivity.EXTRA_USER, userRecord)
            intent.putExtra(TweetActivity.EXTRA_STATUS, clickedElement.originStatus)
            intent.putExtra(TweetActivity.EXTRA_MODE, TweetActivity.MODE_REPLY)
            intent.putExtra(TweetActivity.EXTRA_TEXT, buildString {
                append("@").append(clickedElement.originStatus.user.screenName).append(" ")
                clickedElement.mentions.forEach { mention ->
                    if (!this.toString().contains("@" + mention.screenName) && mention.screenName != userRecord.ScreenName) {
                        append("@").append(mention.screenName).append(" ")
                    }
                }
            })

            startActivity(intent)
            true
        }
    },
    FAVORITE {
        override fun onItemClick(): TimelineFragment.(Status) -> Boolean = proc@{ clickedElement ->
            // 自分のステータスの場合、ナルシストオプションを有効にしていない場合は中断
            if (clickedElement.isOwnedStatus() && !defaultSharedPreferences.getBoolean("pref_narcist", false)) {
                return@proc false
            }

            if (defaultSharedPreferences.getBoolean("pref_dialog_swipe", false) && defaultSharedPreferences.getBoolean("pref_dialog_fav", false)) {
                val dialog = SimpleAlertDialogFragment.Builder(TimelineFragment.DIALOG_REQUEST_ACTION_FAVORITE)
                        .setTitle("確認")
                        .setMessage("お気に入り登録しますか？")
                        .setPositive("OK")
                        .setNegative("キャンセル")
                        .setExtras(Bundle().apply { putSerializable("status", clickedElement) })
                        .build()
                dialog.setTargetFragment(this, TimelineFragment.DIALOG_REQUEST_ACTION_FAVORITE)
                dialog.show(fragmentManager, "dialog_favorite_confirm")
            } else {
                val activity = requireActivity()
                val intent = AsyncCommandService.createFavorite(activity, clickedElement, clickedElement.representUser)
                activity.startService(intent)
            }

            false
        }

        override fun onDialogChose(): TimelineFragment.(Int, Int, Bundle?) -> Unit = { requestCode, which, extras ->
            if (requestCode == TimelineFragment.DIALOG_REQUEST_ACTION_FAVORITE && which == DialogInterface.BUTTON_POSITIVE) {
                val status = extras?.getSerializable("status") as? Status
                if (status != null) {
                    val activity = requireActivity()
                    val intent = AsyncCommandService.createFavorite(activity, status, status.representUser)
                    activity.startService(intent)
                }
            }
        }
    },
    REPOST {
        override fun onItemClick(): TimelineFragment.(Status) -> Boolean = proc@{ clickedElement ->
            if (defaultSharedPreferences.getBoolean("pref_dialog_swipe", false) && defaultSharedPreferences.getBoolean("pref_dialog_rt", false)) {
                val dialog = SimpleAlertDialogFragment.Builder(TimelineFragment.DIALOG_REQUEST_ACTION_REPOST)
                        .setTitle("確認")
                        .setMessage("リツイートしますか？")
                        .setPositive("OK")
                        .setNegative("キャンセル")
                        .setExtras(Bundle().apply { putSerializable("status", clickedElement) })
                        .build()
                dialog.setTargetFragment(this, TimelineFragment.DIALOG_REQUEST_ACTION_REPOST)
                dialog.show(fragmentManager, "dialog_repost_confirm")
            } else {
                val activity = requireActivity()
                val intent = AsyncCommandService.createRepost(activity, clickedElement, clickedElement.representUser)
                activity.startService(intent)
            }

            false
        }

        override fun onDialogChose(): TimelineFragment.(Int, Int, Bundle?) -> Unit = { requestCode, which, extras ->
            if (requestCode == TimelineFragment.DIALOG_REQUEST_ACTION_REPOST && which == DialogInterface.BUTTON_POSITIVE) {
                val status = extras?.getSerializable("status") as? Status
                if (status != null) {
                    val activity = requireActivity()
                    val intent = AsyncCommandService.createRepost(activity, status, status.representUser)
                    activity.startService(intent)
                }
            }
        }
    },
    FAV_AND_REPOST {
        override fun onItemClick(): TimelineFragment.(Status) -> Boolean = proc@{ clickedElement ->
            // 自分のステータスの場合、ナルシストオプションを有効にしていない場合は中断
            if (clickedElement.isOwnedStatus() && !defaultSharedPreferences.getBoolean("pref_narcist", false)) {
                return@proc false
            }

            if (defaultSharedPreferences.getBoolean("pref_dialog_swipe", false) && defaultSharedPreferences.getBoolean("pref_dialog_favrt", false)) {
                val dialog = SimpleAlertDialogFragment.Builder(TimelineFragment.DIALOG_REQUEST_ACTION_FAV_AND_REPOST)
                        .setTitle("確認")
                        .setMessage("お気に入りに登録してRTしますか？")
                        .setPositive("OK")
                        .setNegative("キャンセル")
                        .setExtras(Bundle().apply { putSerializable("status", clickedElement) })
                        .build()
                dialog.setTargetFragment(this, TimelineFragment.DIALOG_REQUEST_ACTION_FAV_AND_REPOST)
                dialog.show(fragmentManager, "dialog_fav_repost_confirm")
            } else {
                val activity = requireActivity()
                val intent = AsyncCommandService.createFavAndRepost(activity, clickedElement, clickedElement.representUser)
                activity.startService(intent)
            }

            false
        }

        override fun onDialogChose(): TimelineFragment.(Int, Int, Bundle?) -> Unit = { requestCode, which, extras ->
            if (requestCode == TimelineFragment.DIALOG_REQUEST_ACTION_FAV_AND_REPOST && which == DialogInterface.BUTTON_POSITIVE) {
                val status = extras?.getSerializable("status") as? Status
                if (status != null) {
                    val activity = requireActivity()
                    val intent = AsyncCommandService.createFavAndRepost(activity, status, status.representUser)
                    activity.startService(intent)
                }
            }
        }
    }
    ;

    abstract fun onItemClick() : TimelineFragment.(Status) -> Boolean
    open fun onDialogChose() : TimelineFragment.(Int, Int, Bundle?) -> Unit = { _, _, _ -> }
}