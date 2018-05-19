package shibafu.yukari.mastodon

import android.net.Uri

object MastodonUtil {

    fun expandFullScreenName(acct: String, url: String): String =
        if (acct.contains('@')) {
            acct
        } else {
            acct + "@" + Uri.parse(url).host
        }
}