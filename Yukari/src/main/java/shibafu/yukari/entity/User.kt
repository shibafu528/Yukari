package shibafu.yukari.entity

import java.io.Serializable

/**
 * お前ら
 */
interface User : Comparable<User>, Serializable {
    /**
     * ID
     */
    val id: Long

    /**
     * URL
     */
    val url: String?
        get() = null

    /**
     * URLのHost
     *
     * ホストの判定が必要な場合に、パース負荷軽減のために参照する。
     * [url]をオーバーライドする場合はこちらもオーバーライドして適切な値を返すようにする。
     */
    val host: String?
        get() = url?.let { throw NotImplementedError("urlを実装している場合、hostも返す必要があります。実装を確認してください。") }

    /**
     * 表示名
     */
    val name: String

    /**
     * 一般的にIDと呼ばれるような、文字列形式のユーザ識別子
     */
    val screenName: String

    /**
     * 非公開アカウント
     */
    val isProtected: Boolean

    /**
     * プロフィールアイコンのURL
     */
    val profileImageUrl: String

    /**
     * 高画質なプロフィールアイコンのURL
     */
    val biggerProfileImageUrl: String

    override fun compareTo(other: User): Int {
        if (this === other) return 0

        if (this.id == other.id) return 0
        if (this.id < other.id) return -1
        return 1
    }
}