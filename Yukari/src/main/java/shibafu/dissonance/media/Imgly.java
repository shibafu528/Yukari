package shibafu.dissonance.media;

/**
 * Created by Shibafu on 13/12/30.
 */
public class Imgly extends LinkMedia {

    public Imgly(String mediaURL) {
        super(mediaURL);
    }

    @Override
    protected String expandMediaURL(String browseURL) {
        ImageMatch matcher = new ImageMatch("http://img\\.ly/([a-zA-Z0-9]+)", "http://img.ly/show/full/%1");
        return matcher.getFullPageUrl(browseURL);
    }

    @Override
    protected String expandThumbURL(String browseURL) {
        ImageMatch matcher = new ImageMatch("http://img\\.ly/([a-zA-Z0-9]+)", "http://img.ly/show/thumb/%1");
        return matcher.getFullPageUrl(browseURL);
    }

    @Override
    public boolean canPreview() {
        return true;
    }
}
