package shibafu.yukari.media;

import org.jsoup.Jsoup;

import java.io.IOException;
import java.net.URL;

/**
 * Created by Shibafu on 16/07/04.
 */
public class Irasutoya extends LinkMedia {
    private String sourceURL = null;

    public Irasutoya(String mediaURL) {
        super(mediaURL);
    }

    private String getSourceURL(final String browseURL) {
        if (sourceURL == null) {
            sourceURL = fetchSynced(() -> {
                try {
                    return Jsoup.parse(new URL(browseURL), 10000).select("meta[name=twitter:image:src]").attr("content");
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
