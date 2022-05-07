package shibafu.yukari.media2.impl;

import androidx.annotation.NonNull;
import shibafu.yukari.media2.MemoizeMedia;

import java.io.IOException;

public class Twimg extends MemoizeMedia {
    /**
     * @param browseUrl メディアの既知のURL
     */
    public Twimg(@NonNull String browseUrl) {
        super(browseUrl);
    }

    @Override
    protected String resolveMediaUrl() throws IOException {
        String browseURL = getBrowseUrl();
        String[] split = browseURL.split(":");
        if (split.length > 2) {
            return split[0] + ":" + split[1] + ":orig";
        } else {
            return browseURL + ":orig";
        }
    }

    @Override
    protected String resolveThumbnailUrl() throws IOException {
        String browseURL = getBrowseUrl();
        String[] split = browseURL.split(":");
        if (split.length > 2) {
            return split[0] + ":" + split[1] + ":thumb";
        } else {
            return browseURL + ":thumb";
        }
    }

    @Override
    public boolean canPreview() {
        return true;
    }
}
