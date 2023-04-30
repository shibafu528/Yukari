package info.shibafu528.yukari.api.mastodon.ws

import com.google.gson.stream.JsonWriter
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter

/**
 * ストリームの購読に使用するパラメータ。
 *
 * @property stream 購読するストリームの名前。有効な値の一覧は[Mastodonのドキュメント](https://docs.joinmastodon.org/methods/streaming/#streams)を参照。
 * @property listener 着信したイベントを受け取るためのリスナー。
 * @property list `list` を購読する場合に、購読対象リストのIDを指定する。
 * @property tag `hashtag` や `hashtag:local` を購読する場合に、購読対象タグの名前を指定する。
 */
data class Subscription(val stream: String,
                        val listener: StreamListener,
                        val list: String? = null,
                        val tag: String? = null) {
    internal fun toMessage(type: String): String {
        val buffer = ByteArrayOutputStream()
        JsonWriter(OutputStreamWriter(buffer)).use { writer ->
            writer.beginObject()
            writer.name("stream").value(stream)
            if (list != null) {
                writer.name("list").value(list)
            }
            if (tag != null) {
                writer.name("tag").value(tag)
            }
            writer.name("type").value(type)
            writer.endObject()
        }
        return buffer.toString()
    }
}
