package shibafu.yukari.mastodon

import android.net.Uri

object MastodonUtil {
    private val compressedHostCache: MutableMap<String, String> = hashMapOf()

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

    fun compressAcct(acct: String): String {
        val splitAcct = acct.split('@')
        if (splitAcct.size != 2) {
            return acct
        }

        val compressedHost = compressedHostCache[splitAcct[1]] ?: splitAcct[1].split('.')
                .joinToString(".") { label ->
                    if (label.length > 3) {
                        "${label.first()}${label.length - 2}${label.last()}"
                    } else {
                        label
                    }
                }
                .also { compressedHostCache[splitAcct[1]] = it }

        return splitAcct[0] + "@" + compressedHost
    }
}