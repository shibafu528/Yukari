package shibafu.yukari.media;

/**
 * Created by Shibafu on 13/12/30.
 */
public class Twitpic extends LinkMedia {

    public Twitpic(String mediaURL) {
        super(mediaURL);
    }

    @Override
    protected String expandMediaURL(String browseURL) {
        ImageMatch matcher = new ImageMatch("http://twitpic\\.com/([a-zA-Z0-9]+)", "https://twitpic.com/show/full/%1");
        return matcher.getFullPageUrl(browseURL);
    }

    @Override
    protected String expandThumbURL(String browseURL) {
        ImageMatch matcher = new ImageMatch("http://twitpic\\.com/([a-zA-Z0-9]+)", "https://twitpic.com/show/thumb/%1");
        return matcher.getFullPageUrl(browseURL);
    }

    @Override
    public boolean canPreview() {
        return true;
    }
}
