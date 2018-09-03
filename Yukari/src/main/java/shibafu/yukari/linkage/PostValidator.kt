package shibafu.yukari.linkage

/**
 * 投稿の文字数計算に関する処理 (入力の文字数を求めたり、最大文字数の情報を照会したり) の実装。
 */
interface PostValidator {
    /**
     * 入力可能な最大文字数を取得します。表示用の目安であり、正確な文字数を表すとは限りません。
     */
    fun getMaxLength(options: Map<String, Any?>): Int

    /**
     * 投稿先サービスの基準に則り、入力テキストの文字数を計測します。表示用の目安であり、正確な文字数を表すとは限りません。
     * @param text 計測対象のテキスト
     * @param options サービス依存の追加情報
     * @return 目安の文字数
     */
    fun getMeasuredLength(text: String, options: Map<String, Any?>): Int
}