package shibafu.yukari.media2;

import android.support.annotation.NonNull;

import java.io.FileNotFoundException;
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
            if (resolvedMediaUrl == null) {
                throw new FileNotFoundException(getBrowseUrl());
            }
        }
        URLConnection connection = new URL(resolvedMediaUrl).openConnection();
        int length = connection.getContentLength();
        InputStream inputStream = connection.getInputStream();
        return createResolveInfo(inputStream, length);
    }

    @Override
    public ResolveInfo resolveThumbnail() throws IOException {
        if (resolvedThumbnailUrl == null) {
            resolvedThumbnailUrl = resolveThumbnailUrl();
            if (resolvedThumbnailUrl == null) {
                throw new FileNotFoundException(getBrowseUrl());
            }
        }
        URLConnection connection = new URL(resolvedThumbnailUrl).openConnection();
        int length = connection.getContentLength();
        InputStream inputStream = connection.getInputStream();
        return createResolveInfo(inputStream, length);
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
    protected abstract String resolveMediaUrl();

    /**
     * サムネイル表示用のメディアのURLを取得します。
     * @return メディアのURL
     */
    protected abstract String resolveThumbnailUrl();
}
