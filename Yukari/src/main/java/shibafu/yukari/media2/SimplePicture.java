package shibafu.yukari.media2;

import android.support.annotation.NonNull;

public class SimplePicture extends MemoizeMedia {
    /**
     * @param browseUrl メディアの既知のURL
     */
    public SimplePicture(@NonNull String browseUrl) {
        super(browseUrl);
    }

    @Override
    protected String resolveMediaUrl() {
        return getBrowseUrl();
    }

    @Override
    protected String resolveThumbnailUrl() {
        return getBrowseUrl();
    }

    @Override
    public boolean canPreview() {
        return true;
    }
}
