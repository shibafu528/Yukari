package shibafu.yukari.mastodon

import android.content.Context
import shibafu.yukari.entity.StatusDraft
import java.io.File
import java.io.IOException

/**
 * Mastodonサーバから取得したデフォルト公開範囲のローカルキャッシュ
 */
class DefaultVisibilityCache(context: Context) {
    private val visibilityByAcct = mutableMapOf<String, StatusDraft.Visibility>()

    init {
        val file = File(context.cacheDir, FILE_NAME)
        if (file.exists()) {
            try {
                file.forEachLine { line ->
                    if (line.isEmpty()) {
                        return@forEachLine
                    }

                    val (acct, visibility) = line.split(',')
                    try {
                        visibilityByAcct[acct] = StatusDraft.Visibility.valueOf(visibility)
                    } catch (e: IllegalArgumentException) {
                        // discard
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    fun save(context: Context) {
        val file = File(context.cacheDir, FILE_NAME)
        file.bufferedWriter().use { writer ->
            visibilityByAcct.forEach { (acct, visibility) ->
                writer.write(acct)
                writer.write(",")
                writer.write(visibility.toString())
                writer.newLine()
            }
        }
    }

    fun get(acct: String): StatusDraft.Visibility = visibilityByAcct[acct] ?: StatusDraft.Visibility.PUBLIC

    fun set(acct: String, visibility: StatusDraft.Visibility) {
        visibilityByAcct[acct] = visibility
    }

    companion object {
        private const val FILE_NAME = "mastodon_default_visibility.csv"
    }
}