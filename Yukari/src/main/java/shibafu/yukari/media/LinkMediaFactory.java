package shibafu.yukari.media;

/**
 * Created by Shibafu on 13/12/30.
 */
public class LinkMediaFactory {

    public static LinkMedia createLinkMedia(String mediaURL) {
        LinkMedia linkMedia = null;

        if (mediaURL.contains("twimg.com")) {
            linkMedia = new Twimg(mediaURL);
        }
        else if (mediaURL.contains("twitpic.com")) {
            linkMedia = new Twitpic(mediaURL);
        }
        else if (mediaURL.contains("/yfrog.com")) {
            linkMedia = new YFrog(mediaURL);
        }
        else if (mediaURL.contains("p.twipple.jp")) {
            linkMedia = new Twipple(mediaURL);
        }
        else if (mediaURL.contains("xvideos.com")) {
            linkMedia = new XVideos(mediaURL);
        }
        else if (mediaURL.contains("instagr.am") || mediaURL.contains("instagram.com")) {
            linkMedia = new Instagram(mediaURL);
        }
        else if (mediaURL.contains("lockerz.com")) {
            linkMedia = new Lockerz(mediaURL);
        }
        else if (mediaURL.contains("photozou.jp")) {
            linkMedia = new Photozou(mediaURL);
        }
        else if (mediaURL.contains("nico.ms/im") || mediaURL.contains("seiga.nicovideo.jp/seiga/im")) {
            linkMedia = new NicoSeiga(mediaURL);
        }
        else if (mediaURL.contains("nico.ms/") || mediaURL.contains("www.nicovideo.jp/watch")) {
            linkMedia = new NicoVideo(mediaURL);
        }
        else if (mediaURL.contains("www.youtube.com/watch?") || mediaURL.contains("youtu.be/")) {
            linkMedia = new YouTube(mediaURL);
        }
        else if (mediaURL.endsWith(".jpg") || mediaURL.endsWith(".jpeg") || mediaURL.endsWith(".png")) {
            linkMedia = new SimplePicture(mediaURL);
        }

        return linkMedia;
    }
}
