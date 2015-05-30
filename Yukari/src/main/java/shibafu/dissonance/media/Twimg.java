package shibafu.dissonance.media;

/**
 * Created by Shibafu on 13/12/30.
 */
public class Twimg extends LinkMedia{

    public Twimg(String mediaURL) {
        super(mediaURL.replace("http://", "https://"));
    }

    @Override
    protected String expandMediaURL(String browseURL) {
        String[] split = browseURL.split(":");
        if (split.length > 2) {
            return browseURL.replace(":" + split[2], ":orig");
        } else {
            return browseURL + ":orig";
        }
    }

    @Override
    protected String expandThumbURL(String browseURL) {
        String[] split = browseURL.split(":");
        if (split.length > 2) {
            return browseURL.replace(":" + split[2], ":thumb");
        } else {
            return browseURL + ":thumb";
        }
    }

    @Override
    public boolean canPreview() {
        return true;
    }
}
