package shibafu.yukari.media;

import org.jsoup.Jsoup;

import java.io.IOException;
import java.net.URL;

/**
 * Created by Shibafu on 13/12/30.
 */
public class Vine extends LinkMedia {

    public Vine(String mediaURL) {
        super(mediaURL);
    }

    @Override
    protected String expandMediaURL(final String browseURL) {
        return fetchSynced(() -> {
            try {
                return Jsoup.parse(new URL(browseURL.replace("http://", "https://")), 10000).select("meta[property=twitter:player:stream]").attr("content");
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        });
    }

    @Override
    protected String expandThumbURL(final String browseURL) {
        return fetchSynced(() -> {
            try {
                return Jsoup.parse(new URL(browseURL.replace("http://", "https://")), 10000).select("meta[property=twitter:image:src]").attr("content");
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        });
    }

    @Override
    public boolean canPreview() {
        return true;
    }
}
