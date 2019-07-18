package shibafu.yukari.mastodon

import android.net.Uri

object MastodonUtil {
    private val REGEX_HOST_COMPRESS = Regex("[a-zA-Z]{4,}")
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

        val compressedHost = compressedHostCache[splitAcct[1]] ?: splitAcct[1]
                .replace(REGEX_HOST_COMPRESS) {
                    "${it.value.first()}${it.value.length - 2}${it.value.last()}"
                }
                .also { compressedHostCache[splitAcct[1]] = it }

        return splitAcct[0] + "@" + compressedHost
    }
}