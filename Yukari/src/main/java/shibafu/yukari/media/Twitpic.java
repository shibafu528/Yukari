package shibafu.yukari.media;

import org.jsoup.Jsoup;

import java.io.IOException;
import java.net.URL;

/**
 * Created by Shibafu on 13/12/30.
 */
public class Twitpic extends LinkMedia {
    private String sourceURL = null;

    public Twitpic(String mediaURL) {
        super(mediaURL);
    }

    private String getSourceURL(final String browseURL) {
        if (sourceURL == null) {
            sourceURL = fetchSynced(() -> {
                try {
                    return Jsoup.parse(new URL(browseURL), 10000).select("meta[name=twitter:image]").attr("value");
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return null;
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
