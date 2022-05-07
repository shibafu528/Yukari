package shibafu.yukari.media2.impl;

import androidx.annotation.NonNull;
import org.jsoup.Jsoup;
import shibafu.yukari.media2.MemoizeMedia;

import java.io.IOException;

public class Pixiv extends MemoizeMedia {
    /**
     * @param browseUrl メディアの既知のURL
     */
    public Pixiv(@NonNull String browseUrl) {
        super(browseUrl);
    }

    @Override
    public String resolveMediaUrl() throws IOException {
        return getBrowseUrl();
    }

    @Override
    public String resolveThumbnailUrl() throws IOException {
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
