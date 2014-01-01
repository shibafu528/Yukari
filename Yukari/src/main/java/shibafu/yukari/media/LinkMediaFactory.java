package shibafu.yukari.media;

/**
 * Created by Shibafu on 13/12/30.
 */
public class LinkMediaFactory {

    public static LinkMedia createLinkMedia(String mediaURL) {
        LinkMedia linkMedia = null;

        if (mediaURL.endsWith(".jpg") || mediaURL.endsWith(".jpeg") || mediaURL.endsWith(".png")) {
            linkMedia = new SimplePicture(mediaURL);
        }
        else if (mediaURL.contains("twitpic.com")) {
            linkMedia = new Twitpic(mediaURL);
        }
        else if (mediaURL.contains("yfrog.com")) {
            linkMedia = new YFrog(mediaURL);
        }
        else if (mediaURL.contains("p.twipple.jp")) {
            linkMedia = new Twipple(mediaURL);
        }
        else if (mediaURL.contains("xvideos.com")) {
            linkMedia = new XVideos(mediaURL);
        }
        else if (mediaURL.contains("instagr.am")) {
            linkMedia = new Instagram(mediaURL);
        }

        return linkMedia;
    }
}
