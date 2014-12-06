package shibafu.yukari.media;

/**
 * Created by Shibafu on 13/12/30.
 */
public class YouTube extends LinkMedia {

    public YouTube(String mediaURL) {
        super(mediaURL);
    }

    @Override
    protected String expandMediaURL(String browseURL) {
        return browseURL;
    }

    @Override
    protected String expandThumbURL(String browseURL) {
        ImageMatch matcher = new ImageMatch("^https?://(?:www\\.youtube\\.com/watch\\?.*v=|youtu\\.be/)([\\w-]+)", "http://i.ytimg.com/vi/%1/default.jpg");
        return matcher.getFullPageUrl(browseURL);
    }

    @Override
    public boolean canPreview() {
        return false;
    }
}
