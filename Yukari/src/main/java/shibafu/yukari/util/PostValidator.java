package shibafu.yukari.util;

import android.support.annotation.NonNull;

public interface PostValidator {
    /**
     * 入力可能な最大文字数を取得します。表示用の目安であり、正確な文字数を表すとは限りません。
     * @return 最大文字数
     */
    int getMaxLength();

    /**
     * 投稿先サービスの基準に則り、入力テキストの文字数を計測します。表示用の目安であり、正確な文字数を表すとは限りません。
     * @param text 計測対象のテキスト
     * @return 目安の文字数
     */
    int getMeasuredLength(@NonNull String text);

    /**
     * 投稿先サービスの基準に則り、投稿可能なテキストであるか判定します。
     * @param text 検査対象のテキスト
     * @return 投稿可能(と思われる)かどうか
     */
    boolean isValidText(@NonNull String text);
}
