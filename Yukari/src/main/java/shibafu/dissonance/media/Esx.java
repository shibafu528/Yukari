package shibafu.dissonance.media;

/**
 * Created by shibafu on 14/09/14.
 */
public class Esx extends LinkMedia{

    public Esx(String mediaURL) {
        super(mediaURL);
    }

    @Override
    protected String expandMediaURL(String browseURL) {
        ImageMatch matcher = new ImageMatch("http://img\\.esx\\.asia/([a-f0-9]+)", "http://img.esx.asia/pic.%1");
        return matcher.getFullPageUrl(browseURL);
    }

    @Override
    protected String expandThumbURL(String browseURL) {
        ImageMatch matcher = new ImageMatch("http://img\\.esx\\.asia/([a-f0-9]+)", "http://img.esx.asia/pic.%1");
        return matcher.getFullPageUrl(browseURL);
    }

    @Override
    public boolean canPreview() {
        return true;
    }
}
