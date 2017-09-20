package shibafu.yukari.media2.impl;

import android.support.annotation.NonNull;
import org.jsoup.Jsoup;
import shibafu.yukari.media2.MemoizeMedia;

import java.io.IOException;

public class SixHundredEUR extends MemoizeMedia {
    /**
     * @param browseUrl メディアの既知のURL
     */
    public SixHundredEUR(@NonNull String browseUrl) {
        super(browseUrl);
    }

    @Override
    protected String resolveMediaUrl() throws IOException {
        return Jsoup.connect(getBrowseUrl())
                .timeout(10000)
                .get()
                .select("meta[name=twitter:image:src]")
                .attr("content");
    }

    @Override
    protected String resolveThumbnailUrl() throws IOException {
        return resolveMediaUrl();
    }

    @Override
    public boolean canPreview() {
        return true;
    }
}
