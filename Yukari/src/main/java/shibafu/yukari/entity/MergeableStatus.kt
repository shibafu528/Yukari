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
}