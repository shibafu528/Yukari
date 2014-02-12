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
        ImageMatch matcher = new ImageMatch("http://instagr(.am|am.com)/p/([a-zA-Z0-9-]+)", "http://instagr.am/p/%2/media/?size=l");
        return matcher.getFullPageUrl(browseURL);
    }

    @Override
    protected String expandThumbURL(String browseURL) {
        ImageMatch matcher = new ImageMatch("http://instagr(.am|am.com)/p/([a-zA-Z0-9-]+)", "http://instagr.am/p/%2/media/?size=t");
        return matcher.getFullPageUrl(browseURL);
    }

    @Override
    public boolean canPreview() {
        return true;
    }
}
