package shibafu.yukari.media2.impl;

import android.support.annotation.NonNull;
import org.jsoup.Jsoup;
import shibafu.yukari.media2.MemoizeMedia;

import java.io.IOException;
import java.net.URL;

public class D250g2 extends MemoizeMedia {
    /**
     * @param browseUrl メディアの既知のURL
     */
    public D250g2(@NonNull String browseUrl) {
        super(browseUrl);
    }

    @Override
    protected String resolveMediaUrl() throws IOException {
        // twitpic.com/d250g2 か d250g2.com の何らかのURLが渡ってくる
        if (!getBrowseUrl().contains("twitpic.com")) {
            return Jsoup.parse(new URL(getBrowseUrl()), 10000)
                    .select("meta[name=twitter:image:src]")
                    .attr("content");
        }
        return "http://d250g2.com/d250g2.jpg";
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
