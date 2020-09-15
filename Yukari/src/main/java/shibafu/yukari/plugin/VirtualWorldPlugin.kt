package shibafu.yukari.plugin

import android.content.Intent
import android.os.Handler
import android.os.Looper
import info.shibafu528.yukari.exvoice.MRuby
import info.shibafu528.yukari.exvoice.pluggaloid.Keyword
import info.shibafu528.yukari.exvoice.pluggaloid.Plugin
import info.shibafu528.yukari.exvoice.pluggaloid.RestKeywords
import info.shibafu528.yukari.exvoice.pluggaloid.Spell
import shibafu.yukari.activity.TweetActivity

/**
 * Yukari::World 仮想World & Spell
 */
class VirtualWorldPlugin(mRuby: MRuby) : Plugin(mRuby, "yukari") {
    init {
        mRuby.requireAssets("mrb/yukari/world.rb")
    }

    @Spell("compose", constraints = ["yukari_world"])
    fun compose(world: Any, @Keyword("body") body: String?, @RestKeywords options: Map<String, Any?>) {
        val intent = Intent(context, TweetActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(TweetActivity.EXTRA_MODE, TweetActivity.MODE_TWEET)
            putExtra(TweetActivity.EXTRA_TEXT, body)
        }
        onMainThread {
            context.startActivity(intent)
        }
    }

    @Spell("compose", constraints = ["yukari_world", "twitter_tweet"])
    fun compose(world: Any, message: Map<String, Any?>, @Keyword("body") body: String?, @RestKeywords options: Map<String, Any?>) {
        val intent = Intent(context, TweetActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(TweetActivity.EXTRA_MODE, TweetActivity.MODE_REPLY)
            putExtra(TweetActivity.EXTRA_IN_REPLY_TO, message["perma_link"]?.toString())
            putExtra(TweetActivity.EXTRA_TEXT, body)
        }
        onMainThread {
            context.startActivity(intent)
        }
    }

    inline fun onMainThread(crossinline action: () -> Unit) {
        Handler(Looper.getMainLooper()).post { action() }
    }
}