package shibafu.yukari.entity

import java.io.Serializable

/**
 * [Status] にぶら下がっているメンションの情報
 */
interface Mention : Serializable {
    /**
     * ID
     */
    val id: Long

    /**
     * 一般的にIDと呼ばれるような、文字列形式のユーザ識別子
     */
    val screenName: String
}