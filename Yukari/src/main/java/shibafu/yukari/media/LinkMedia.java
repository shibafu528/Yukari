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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        LinkMedia linkMedia = (LinkMedia) o;

        if (browseURL != null ? !browseURL.equals(linkMedia.browseURL) : linkMedia.browseURL != null)
            return false;
        if (mediaURL != null ? !mediaURL.equals(linkMedia.mediaURL) : linkMedia.mediaURL != null)
            return false;
        if (thumbURL != null ? !thumbURL.equals(linkMedia.thumbURL) : linkMedia.thumbURL != null)
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = browseURL != null ? browseURL.hashCode() : 0;
        result = 31 * result + (mediaURL != null ? mediaURL.hashCode() : 0);
        result = 31 * result + (thumbURL != null ? thumbURL.hashCode() : 0);
        return result;
    }
}
