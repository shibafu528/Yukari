package shibafu.yukari.entity

/**
 * 返信先の識別情報を格納するクラス。
 *
 * URL形式での表現を1つ持つことが必須要件で、Providerの仕様に応じて追加の識別情報を持たせても良い。
 */
class InReplyToId(val url: String) {
    private val perProviderId = HashMap<String, String>()

    operator fun get(apiType: Int, host: String): String? = perProviderId["$apiType@$host"]

    operator fun set(apiType: Int, host: String, value: String) {
        perProviderId["$apiType@$host"] = value
    }
}