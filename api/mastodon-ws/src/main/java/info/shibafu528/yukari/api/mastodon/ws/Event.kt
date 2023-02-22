package info.shibafu528.yukari.api.mastodon.ws

/**
 * サーバから着信したイベント。
 *
 * [API Document](https://docs.joinmastodon.org/methods/streaming/#events-11)
 */
internal data class Event(val stream: List<String>,
                          val event: String,
                          val payload: String?)

