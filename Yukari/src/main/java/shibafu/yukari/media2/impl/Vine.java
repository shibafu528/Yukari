package shibafu.yukari.media2.impl;

import androidx.annotation.NonNull;
import org.jsoup.Jsoup;
import shibafu.yukari.media2.MemoizeMedia;

import java.io.IOException;
import java.net.URL;

public class Vine extends MemoizeMedia {
    /**
     * @param browseUrl メディアの既知のURL
     */
    public Vine(@NonNull String browseUrl) {
        super(browseUrl);
    }

    @Override
    protected String resolveMediaUrl() throws IOException {
        return Jsoup.parse(new URL(getBrowseUrl().replace("http://", "https://")), 10000)
                .select("meta[property=twitter:player:stream]")
                .attr("content");
    }

    @Override
    protected String resolveThumbnailUrl() throws IOException {
        return Jsoup.parse(new URL(getBrowseUrl().replace("http://", "https://")), 10000)
                .select("meta[property=twitter:image:src]")
                .attr("content");
    }

    @Override
    public boolean canPreview() {
        return false;
    }
}
