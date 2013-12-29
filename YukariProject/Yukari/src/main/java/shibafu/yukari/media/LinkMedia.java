package shibafu.yukari.media;

import java.io.Serializable;

/**
 * Created by Shibafu on 13/12/30.
 */
public abstract class LinkMedia implements Serializable{
    private String mediaURL;
    private String thumbURL;

    public LinkMedia(String mediaURL) {
        this.mediaURL = mediaURL;
        this.thumbURL = extractThumbURL(mediaURL);
    }

    protected abstract String extractThumbURL(String mediaURL);

    @Override
    public String toString() {
        return mediaURL;
    }

    public String getMediaURL() {
        return mediaURL;
    }

    public String getThumbURL() {
        return thumbURL;
    }
}
