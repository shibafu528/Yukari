package shibafu.yukari.media2.impl;

import android.support.annotation.NonNull;
import org.jsoup.Jsoup;
import shibafu.yukari.media2.MemoizeMedia;

import java.io.IOException;
import java.net.URL;

public class Twitpic extends MemoizeMedia {
    /**
     * @param browseUrl メディアの既知のURL
     */
    public Twitpic(@NonNull String browseUrl) {
        super(browseUrl);
    }

    @Override
    protected String resolveMediaUrl() throws IOException {
        return Jsoup.parse(new URL(getBrowseUrl()), 10000)
                .select("meta[name=twitter:image]")
                .attr("value");
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
