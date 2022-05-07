package shibafu.yukari.media2.impl;

import androidx.annotation.NonNull;
import org.jsoup.Jsoup;
import shibafu.yukari.media2.MemoizeMedia;

import java.io.IOException;
import java.net.URL;
import java.util.regex.Pattern;

public class Irasutoya extends MemoizeMedia {
    public static final Pattern URL_PATTERN = Pattern.compile("www\\.irasutoya\\.com/\\d{4}/\\d{2}/blog-post_\\d+\\.html");

    /**
     * @param browseUrl メディアの既知のURL
     */
    public Irasutoya(@NonNull String browseUrl) {
        super(browseUrl);
    }

    @Override
    protected String resolveMediaUrl() throws IOException {
        return Jsoup.parse(new URL(getBrowseUrl()), 10000)
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
