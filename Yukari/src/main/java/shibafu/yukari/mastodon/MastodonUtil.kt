package shibafu.yukari.mastodon

import android.net.Uri

object MastodonUtil {

    fun expandFullScreenName(acct: String, url: String): String =
        if (acct.contains('@')) {
            acct
        } else {
            acct + "@" + Uri.parse(url).host
        }

    fun splitFullScreenName(fullScreenName: String): Pair<String, String> {
        if (!fullScreenName.contains("@")) {
            return fullScreenName to ""
        }

        val split = fullScreenName.split("@")
        return split[0] to split[1]
    }
}