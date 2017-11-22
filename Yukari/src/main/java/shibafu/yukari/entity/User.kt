package shibafu.yukari.entity

/**
 * お前ら
 */
interface User : Comparable<User> {
    /**
     * ID
     */
    val id: Long

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