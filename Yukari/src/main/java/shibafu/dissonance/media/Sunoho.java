package shibafu.dissonance.media;

/**
 * Created by shibafu on 14/09/14.
 */
public class Sunoho extends LinkMedia{

    public Sunoho(String mediaURL) {
        super(mediaURL);
    }

    @Override
    protected String expandMediaURL(String browseURL) {
        ImageMatch matcher = new ImageMatch("http://sunoho\\.com/p/i/([a-f0-9]+)", "http://sunoho.com/p/i/%1.png");
        return matcher.getFullPageUrl(browseURL);
    }

    @Override
    protected String expandThumbURL(String browseURL) {
        ImageMatch matcher = new ImageMatch("http://sunoho\\.com/p/i/([a-f0-9]+)", "http://sunoho.com/p/i/%1.png");
        return matcher.getFullPageUrl(browseURL);
    }

    @Override
    public boolean canPreview() {
        return true;
    }
}
