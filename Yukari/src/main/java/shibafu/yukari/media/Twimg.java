package shibafu.yukari.media;

/**
 * Created by Shibafu on 13/12/30.
 */
public class Twimg extends LinkMedia{

    public Twimg(String mediaURL) {
        super(mediaURL.replace("http://", "https://"));
    }

    @Override
    protected String expandMediaURL(String browseURL) {
        return browseURL + ":orig";
    }

    @Override
    protected String expandThumbURL(String browseURL) {
        return browseURL + ":thumb";
    }

    @Override
    public boolean canPreview() {
        return true;
    }
}