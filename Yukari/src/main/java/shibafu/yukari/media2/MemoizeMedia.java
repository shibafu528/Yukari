package shibafu.yukari.media2;

import android.support.annotation.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

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
        }
        URLConnection connection = new URL(resolvedMediaUrl).openConnection();
        int length = connection.getContentLength();
        InputStream inputStream = connection.getInputStream();
        return new ResolveInfo(inputStream, length);
    }

    @Override
    public ResolveInfo resolveThumbnail() throws IOException {
        if (resolvedThumbnailUrl == null) {
            resolvedThumbnailUrl = resolveThumbnailUrl();
        }
        URLConnection connection = new URL(resolvedThumbnailUrl).openConnection();
        int length = connection.getContentLength();
        InputStream inputStream = connection.getInputStream();
        return new ResolveInfo(inputStream, length);
    }

    /**
     * オリジナルまたはプレビュー表示用のメディアのURLを取得します。
     * @return メディアのURL
     */
    protected abstract String resolveMediaUrl();

    /**
     * サムネイル表示用のメディアのURLを取得します。
     * @return メディアのURL
     */
    protected abstract String resolveThumbnailUrl();
}
