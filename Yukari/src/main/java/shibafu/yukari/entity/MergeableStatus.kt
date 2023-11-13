package shibafu.yukari.entity

/**
 * タイムライン上でマージ可能な [Status] であることを表すインターフェース。
 */
interface MergeableStatus {
    /**
     * レシーバーと指定されたオブジェクトの間で、マージした際の表示優先度を比較する。
     *
     * 戻り値が取りうる値は [Comparable.compareTo] と同じで、昇順に並び替えて先頭に来たものが最優先となる。
     */
    fun compareMergePriorityTo(other: Status): Int

    /**
     * レシーバーを、タイムライン上でマージされた状態から切り離す。
     *
     * 引数には他にマージされていたステータスが与えられる。必要に応じて他のステータスのサーバーローカル情報などをコピーするために使用できる。
     *
     * 戻り値として、レシーバー自身または切り離した状態を表す新しいステータスを返すこと。
     */
    fun unmerge(followers: List<Status>): Status
}