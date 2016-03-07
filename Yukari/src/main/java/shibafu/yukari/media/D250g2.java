package shibafu.yukari.media;

import org.jsoup.Jsoup;

import java.io.IOException;
import java.net.URL;

/**
 * Created by Shibafu on 13/12/30.
 */
public class D250g2 extends LinkMedia {
    private String sourceURL = null;

    public D250g2(String mediaURL) {
        super(mediaURL);
    }

    private String getSourceURL(final String browseURL) {
        if (sourceURL == null) {
            sourceURL = fetchSynced(() -> {
                // twitpic.com/d250g2 か d250g2.com の何らかのURLが渡ってくる
                if (!browseURL.contains("twitpic.com")) {
                    try {
                        return Jsoup.parse(new URL(browseURL), 10000).select("meta[name=twitter:image:src]").attr("content");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                return "http://d250g2.com/d250g2.jpg";
            });
        }
        return sourceURL;
    }

    @Override
    protected String expandMediaURL(String browseURL) {
        return getSourceURL(browseURL);
    }

    @Override
    protected String expandThumbURL(String browseURL) {
        return getSourceURL(browseURL);
    }

    @Override
    public boolean canPreview() {
        return true;
    }
}
