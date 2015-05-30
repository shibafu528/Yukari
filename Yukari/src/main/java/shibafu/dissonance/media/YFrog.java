package shibafu.dissonance.media;

/**
 * Created by Shibafu on 13/12/30.
 */
public class YFrog extends LinkMedia {

    public YFrog(String mediaURL) {
        super(mediaURL);
    }

    @Override
    protected String expandMediaURL(String browseURL) {
        ImageMatch matcher = new ImageMatch("http://yfrog\\.com/([a-zA-Z0-9]+)", "http://yfrog.com/%1:medium");
        return matcher.getFullPageUrl(browseURL);
    }

    @Override
    protected String expandThumbURL(String browseURL) {
        ImageMatch matcher = new ImageMatch("http://yfrog\\.com/([a-zA-Z0-9]+)", "http://yfrog.com/%1:small");
        return matcher.getFullPageUrl(browseURL);
    }

    @Override
    public boolean canPreview() {
        return true;
    }
}
