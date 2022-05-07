package shibafu.yukari.media2.impl;

import androidx.annotation.NonNull;
import shibafu.yukari.media2.MemoizeMedia;

import java.io.IOException;

public class TwitterVideo extends MemoizeMedia {
    private String wellknownThumbnailUrl;

    /**
     * @param browseUrl メディアの既知のURL
     */
    public TwitterVideo(@NonNull String browseUrl) {
        super(browseUrl);
    }

    public TwitterVideo(@NonNull String mediaUrl, @NonNull String thumbnailUrl) {
        super(mediaUrl);
        wellknownThumbnailUrl = thumbnailUrl;
    }

    @Override
    protected String resolveMediaUrl() throws IOException {
        return getBrowseUrl();
    }

    @Override
    protected String resolveThumbnailUrl() throws IOException {
        if (wellknownThumbnailUrl != null) {
            return wellknownThumbnailUrl;
        }
        return getBrowseUrl();
    }

    @Override
    public boolean canPreview() {
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TwitterVideo)) return false;
        if (!super.equals(o)) return false;

        TwitterVideo that = (TwitterVideo) o;

        return wellknownThumbnailUrl != null ? wellknownThumbnailUrl.equals(that.wellknownThumbnailUrl) : that.wellknownThumbnailUrl == null;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (wellknownThumbnailUrl != null ? wellknownThumbnailUrl.hashCode() : 0);
        return result;
    }
}
