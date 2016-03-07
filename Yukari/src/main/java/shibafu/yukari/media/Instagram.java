package shibafu.yukari.media;

/**
 * Created by Shibafu on 13/12/30.
 */
public class Instagram extends LinkMedia {

    public Instagram(String mediaURL) {
        super(mediaURL);
    }

    @Override
    protected String expandMediaURL(String browseURL) {
        if (browseURL.startsWith("http://")) {
            browseURL = browseURL.replace("http://", "https://");
        }
        if (!browseURL.endsWith("/")) {
            browseURL += "/";
        }
        browseURL += "media/?size=l";
        return browseURL;
    }

    @Override
    protected String expandThumbURL(String browseURL) {
        if (browseURL.startsWith("http://")) {
            browseURL = browseURL.replace("http://", "https://");
        }
        if (!browseURL.endsWith("/")) {
            browseURL += "/";
        }
        browseURL += "media/?size=t";
        return browseURL;
    }

    @Override
    public boolean canPreview() {
        return true;
    }
}
