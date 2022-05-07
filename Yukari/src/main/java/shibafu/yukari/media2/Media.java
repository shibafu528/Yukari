package shibafu.yukari.media2;

import android.app.DownloadManager;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.HttpURLConnection;

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
     * ダウンロードリクエストを生成します。
     * <b>この操作は通信を伴う可能性があるため、UIスレッドで呼び出してはいけません。</b>
     * @return {@link DownloadManager.Request} のインスタンス。何らかの理由でダウンロードできないメディアの場合、nullを返す。
     * @throws IOException 通信やファイルシステムのエラーによって中断された場合にスロー
     */
    @WorkerThread
    @Nullable
    public abstract DownloadManager.Request getDownloadRequest() throws IOException;

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

    protected static ResolveInfo createResolveInfo(InputStream stream, int contentLength, Closeable... closeables) {
        return new ResolveInfo(stream, contentLength, closeables);
    }

    /**
     * 閲覧用URL等を基に画像や動画の実体を検索した結果。データ本体を取得するためのストリームや追加情報を持つ。
     */
    public static class ResolveInfo {
        private InputStream stream;
        private int contentLength;
        private Closeable[] closables;

        private ResolveInfo(InputStream stream, int contentLength, Closeable... closeables) {
            this.stream = stream;
            this.contentLength = contentLength;
            this.closables = closeables;
        }

        public InputStream getStream() {
            return stream;
        }

        public int getContentLength() {
            return contentLength;
        }

        @SuppressWarnings("ForLoopReplaceableByForEach")
        public void dispose() {
            if (closables != null) {
                for (int i = 0; i < closables.length; i++) {
                    try {
                        closables[i].close();
                    } catch (IOException ignored) {}
                }
            }
        }
    }

    public static class CloseableHttpURLConnectionWrapper implements Closeable {
        private HttpURLConnection connection;

        public CloseableHttpURLConnectionWrapper(HttpURLConnection connection) {this.connection = connection;}

        @Override
        public void close() throws IOException {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
