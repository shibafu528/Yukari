package shibafu.yukari.media;

import java.io.Serializable;

/**
 * Created by Shibafu on 13/12/30.
 */
public abstract class LinkMedia implements Serializable{
    private String browseURL;
    private String mediaURL;
    private String thumbURL;

    public LinkMedia(String browseURL) {
        this.browseURL = browseURL;
        this.mediaURL = expandMediaURL(browseURL);
        this.thumbURL = expandThumbURL(browseURL);
    }

    protected abstract String expandMediaURL(String browseURL);

    protected abstract String expandThumbURL(String browseURL);

    public abstract boolean canPreview();

    @Override
    public String toString() {
        return browseURL;
    }

    public String getBrowseURL() {
        return browseURL;
    }

    public String getThumbURL() {
        return thumbURL;
    }

    public String getMediaURL() {
        return mediaURL;
    }
}
