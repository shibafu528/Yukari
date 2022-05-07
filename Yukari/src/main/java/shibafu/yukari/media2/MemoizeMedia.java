package shibafu.yukari.media2;

import android.app.DownloadManager;
import android.net.Uri;
import android.os.Environment;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * {@link Media}に一度解決したメディア本体のURLをキャッシュする機能を付けたもの。
 */
public abstract class MemoizeMedia extends Media {
    private String resolvedMediaUrl;
    private String resolvedThumbnailUrl;

    /**
     * @param browseUrl メディアの既知のURL
     */
    public MemoizeMedia(@NonNull String browseUrl) {
        super(browseUrl);
    }

    @Override
    public ResolveInfo resolveMedia() throws IOException {
        if (resolvedMediaUrl == null) {
            resolvedMediaUrl = resolveMediaUrl();
            if (resolvedMediaUrl == null) {
                throw new FileNotFoundException(getBrowseUrl());
            }
        }
        HttpURLConnection connection = (HttpURLConnection) new URL(resolvedMediaUrl).openConnection();
        try {
            connection = resolveRedirect(connection);
            int length = connection.getContentLength();
            InputStream inputStream = connection.getInputStream();
            return createResolveInfo(inputStream, length, new CloseableHttpURLConnectionWrapper(connection));
        } catch (IOException e) {
            if (connection != null) {
                connection.disconnect();
            }
            throw e;
        }
    }

    @Override
    public ResolveInfo resolveThumbnail() throws IOException {
        if (resolvedThumbnailUrl == null) {
            resolvedThumbnailUrl = resolveThumbnailUrl();
            if (resolvedThumbnailUrl == null) {
                throw new FileNotFoundException(getBrowseUrl());
            }
        }
        HttpURLConnection connection = (HttpURLConnection) new URL(resolvedThumbnailUrl).openConnection();
        try {
            connection = resolveRedirect(connection);
            int length = connection.getContentLength();
            InputStream inputStream = connection.getInputStream();
            return createResolveInfo(inputStream, length, new CloseableHttpURLConnectionWrapper(connection));
        } catch (IOException e) {
            if (connection != null) {
                connection.disconnect();
            }
            throw e;
        }
    }

    @Nullable
    @Override
    public DownloadManager.Request getDownloadRequest() throws IOException {
        if (resolvedMediaUrl == null) {
            resolvedMediaUrl = resolveMediaUrl();
            if (resolvedMediaUrl == null) {
                throw new FileNotFoundException(getBrowseUrl());
            }
        }
        Uri uri = Uri.parse(resolvedMediaUrl);
        DownloadManager.Request request = new DownloadManager.Request(uri);
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, uri.getLastPathSegment().replace(":orig", ""));
        return request;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MemoizeMedia)) return false;
        if (!super.equals(o)) return false;

        MemoizeMedia that = (MemoizeMedia) o;

        if (resolvedMediaUrl != null ? !resolvedMediaUrl.equals(that.resolvedMediaUrl) : that.resolvedMediaUrl != null)
            return false;
        return resolvedThumbnailUrl != null ? resolvedThumbnailUrl.equals(that.resolvedThumbnailUrl) : that.resolvedThumbnailUrl == null;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (resolvedMediaUrl != null ? resolvedMediaUrl.hashCode() : 0);
        result = 31 * result + (resolvedThumbnailUrl != null ? resolvedThumbnailUrl.hashCode() : 0);
        return result;
    }

    /**
     * オリジナルまたはプレビュー表示用のメディアのURLを取得します。
     * @return メディアのURL
     */
    protected abstract String resolveMediaUrl() throws IOException;

    /**
     * サムネイル表示用のメディアのURLを取得します。
     * @return メディアのURL
     */
    protected abstract String resolveThumbnailUrl() throws IOException;

    /**
     * プロトコル跨ぎを含めてリダイレクトをさばいた状態のHttpURLConnectionを作ります。
     * @param connection リダイレクトの解決を行いたい接続
     * @return リダイレクト解決済みの接続
     * @throws IOException
     */
    private static HttpURLConnection resolveRedirect(HttpURLConnection connection) throws IOException {
        connection.setInstanceFollowRedirects(true);
        connection.connect();
        while (connection.getResponseCode() == HttpURLConnection.HTTP_MOVED_PERM ||
                connection.getResponseCode() == HttpURLConnection.HTTP_MOVED_TEMP) {
            String redirectUrl = connection.getHeaderField("Location");
            connection.disconnect();

            connection = (HttpURLConnection) new URL(redirectUrl).openConnection();
            connection.setInstanceFollowRedirects(true);
            connection.connect();
        }
        return connection;
    }
}
