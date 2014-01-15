package shibafu.yukari.media;

/**
 * Created by Shibafu on 13/12/30.
 */
public class Twimg extends LinkMedia{

    public Twimg(String mediaURL) {
        super(mediaURL);
    }

    @Override
    protected String expandMediaURL(String browseURL) {
        return browseURL;
    }

    @Override
    protected String expandThumbURL(String browseURL) {
        return browseURL + ":thumb";
    }
}
