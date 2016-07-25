package shibafu.yukari.media;

import org.jsoup.Jsoup;

import java.io.IOException;

/**
 * Created by Shibafu on 13/12/30.
 */
public class XVideos extends LinkMedia {

    public XVideos(String mediaURL) {
        super(mediaURL);
    }

    @Override
    protected String expandMediaURL(String browseURL) {
        return browseURL;
    }

    @Override
    protected String expandThumbURL(final String browseURL) {
        return fetchSynced(() -> {
            try {
                return Jsoup.connect(browseURL)
                        .timeout(10000)
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
        return false;
    }
}
