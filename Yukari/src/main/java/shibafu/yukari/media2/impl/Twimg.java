package shibafu.yukari.media2.impl;

import android.support.annotation.NonNull;
import shibafu.yukari.media2.MemoizeMedia;

public class Twimg extends MemoizeMedia {
    /**
     * @param browseUrl メディアの既知のURL
     */
    public Twimg(@NonNull String browseUrl) {
        super(browseUrl);
    }

    @Override
    protected String resolveMediaUrl() {
        String browseURL = getBrowseUrl();
        String[] split = browseURL.split(":");
        if (split.length > 2) {
            return browseURL.replace(":" + split[2], ":orig");
        } else {
            return browseURL + ":orig";
        }
    }

    @Override
    protected String resolveThumbnailUrl() {
        String browseURL = getBrowseUrl();
        String[] split = browseURL.split(":");
        if (split.length > 2) {
            return browseURL.replace(":" + split[2], ":thumb");
        } else {
            return browseURL + ":thumb";
        }
    }

    @Override
    public boolean canPreview() {
        return true;
    }
}
