package shibafu.yukari.media;

/**
 * Created by Shibafu on 13/12/30.
 */
public class Meshi extends LinkMedia {

    public Meshi(String mediaURL) {
        super(mediaURL);
    }

    @Override
    protected String expandMediaURL(String browseURL) {
        ImageMatch matcher = new ImageMatch("https?://mstr\\.in/photos/([a-z0-9]+)", "https://pic.mstr.in/images/%1.jpg");
        return matcher.getFullPageUrl(browseURL);
    }

    @Override
    protected String expandThumbURL(String browseURL) {
        return expandMediaURL(browseURL);
    }

    @Override
    public boolean canPreview() {
        return true;
    }
}
