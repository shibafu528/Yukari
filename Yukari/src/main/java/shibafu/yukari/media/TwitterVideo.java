package shibafu.yukari.media;

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        TwitterVideo that = (TwitterVideo) o;

        return videoURL.equals(that.videoURL);

    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + videoURL.hashCode();
        return result;
    }
}
