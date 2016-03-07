package shibafu.yukari.media;

import org.jsoup.Jsoup;

import java.io.IOException;

/**
 * Created by Shibafu on 13/12/30.
 */
public class Nijie extends LinkMedia {

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/47.0.2526.106 Safari/537.36";

    public Nijie(String mediaURL) {
        super(mediaURL);
    }

    @Override
    protected String expandMediaURL(final String browseURL) {
        return fetchSynced(() -> {
            try {
                return Jsoup.connect(browseURL.replace("http://", "https://"))
                        .timeout(10000)
                        .userAgent(USER_AGENT)
                        .get()
                        .select("meta[property=og:image]")
                        .attr("content")
                        .replace("/sp", "");
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
                return Jsoup.connect(browseURL.replace("http://", "https://"))
                        .timeout(10000)
                        .userAgent(USER_AGENT)
                        .get()
                        .select("meta[property=og:image]")
                        .attr("content");
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
