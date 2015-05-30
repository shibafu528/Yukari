package shibafu.dissonance.media;

/**
 * Created by Shibafu on 13/12/30.
 */
public class TwitterVideo extends LinkMedia{
    private String videoURL;

    public TwitterVideo(String browseURL) {
        super(browseURL);
        this.videoURL = browseURL;
    }

    public TwitterVideo(String videoURL, String thumbURL) {
        super(thumbURL);
        this.videoURL = videoURL;
    }

    @Override
    public String getBrowseURL() {
        return videoURL;
    }

    @Override
    public String getMediaURL() {
        return videoURL;
    }

    @Override
    protected String expandMediaURL(String browseURL) { return browseURL; }

    @Override
    protected String expandThumbURL(String browseURL) { return browseURL; }

    @Override
    public boolean canPreview() {
        return true;
    }
}
