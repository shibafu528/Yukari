package shibafu.yukari.media2.impl;

import android.support.annotation.NonNull;
import org.jsoup.Jsoup;
import shibafu.yukari.media2.MemoizeMedia;

import java.io.IOException;

public class XVideos extends MemoizeMedia {
    /**
     * @param browseUrl メディアの既知のURL
     */
    public XVideos(@NonNull String browseUrl) {
        super(browseUrl);
    }

    @Override
    protected String resolveMediaUrl() throws IOException {
        return getBrowseUrl();
    }

    @Override
    protected String resolveThumbnailUrl() throws IOException {
        return Jsoup.connect(getBrowseUrl())
                .timeout(10000)
                .get()
                .select("meta[property=og:image]")
                .attr("content");
    }

    @Override
    public boolean canPreview() {
        return false;
    }
}
