package shibafu.yukari.media2;

import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;

/**
 * 単一の画像や動画のURLと取得方法の実装を表す。
 */
public abstract class Media implements Serializable {
    @NonNull
    private String browseUrl;

    /**
     * @param browseUrl メディアの既知のURL
     */
    public Media(@NonNull String browseUrl) {
        this.browseUrl = browseUrl;
    }

    /**
     * ブラウザ閲覧や共有用のURLを取得します。
     * @return URL
     */
    @NonNull
    public String getBrowseUrl() {
        return browseUrl;
    }

    /**
     * オリジナルまたはプレビュー表示用のメディア情報を取得します。
     * <b>この操作は通信を伴う可能性があるため、UIスレッドで呼び出してはいけません。</b>
     * @return 解決されたメディア情報
     * @throws IOException 通信やファイルシステムのエラーによって中断された場合にスロー
     */
    @WorkerThread
    public abstract ResolveInfo resolveMedia() throws IOException;

    /**
     * サムネイル表示用のメディア情報を取得します。
     * 専用のデータが存在しない場合、{@link #resolveMedia()}と同様の結果になる場合もあります。
     * <b>この操作は通信を伴う可能性があるため、UIスレッドで呼び出してはいけません。</b>
     * @return 解決されたメディア情報
     * @throws IOException 通信やファイルシステムのエラーによって中断された場合にスロー
     */
    @WorkerThread
    public abstract ResolveInfo resolveThumbnail() throws IOException;

    /**
     * プレビュー機能を使うことができるかどうかを返します。
     * @return プレビュー対応の可否
     */
    public abstract boolean canPreview();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Media)) return false;

        Media media = (Media) o;

        return browseUrl.equals(media.browseUrl);
    }

    @Override
    public int hashCode() {
        return browseUrl.hashCode();
    }

    /**
     * 閲覧用URL等を基に画像や動画の実体を検索した結果。データ本体を取得するためのストリームや追加情報を持つ。
     */
    public static class ResolveInfo {
        private InputStream stream;
        private long contentLength;

        /*internal*/ ResolveInfo(InputStream stream, long contentLength) {
            this.stream = stream;
            this.contentLength = contentLength;
        }

        public InputStream getStream() {
            return stream;
        }

        public long getContentLength() {
            return contentLength;
        }
    }
}
