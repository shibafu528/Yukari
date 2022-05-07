package shibafu.yukari.media2.impl;

import androidx.annotation.NonNull;
import shibafu.yukari.media2.MemoizeMedia;

import java.io.IOException;

public class SimplePicture extends MemoizeMedia {
    /**
     * @param browseUrl メディアの既知のURL
     */
    public SimplePicture(@NonNull String browseUrl) {
        super(browseUrl);
    }

    @Override
    protected String resolveMediaUrl() throws IOException {
        return getBrowseUrl();
    }

    @Override
    protected String resolveThumbnailUrl() throws IOException {
        return getBrowseUrl();
    }

    @Override
    public boolean canPreview() {
        return true;
    }
}
