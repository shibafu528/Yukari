package shibafu.yukari.media;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Created by Shibafu on 13/12/30.
 */
public class LinkMediaFactory {
    private static Map<String, LinkMedia> instanceQueue = new LinkedHashMap<String, LinkMedia>() {
        @Override
        protected boolean removeEldestEntry(Entry eldest) {
            return size() > 32;
        }
    };

    public static synchronized LinkMedia newInstance(String mediaURL) {
        LinkMedia linkMedia = instanceQueue.get(mediaURL);

        if (linkMedia == null) {
            if (mediaURL.contains("twimg.com")) {
                linkMedia = new Twimg(mediaURL);
            } else if (mediaURL.contains("twitpic.com")) {
                linkMedia = new Twitpic(mediaURL);
            } else if (mediaURL.contains("/yfrog.com")) {
                linkMedia = new YFrog(mediaURL);
            } else if (mediaURL.contains("p.twipple.jp")) {
                linkMedia = new Twipple(mediaURL);
            } else if (mediaURL.contains("xvideos.com")) {
                linkMedia = new XVideos(mediaURL);
            } else if (mediaURL.contains("instagr.am/p/") || mediaURL.contains("instagram.com/p/")) {
                linkMedia = new Instagram(mediaURL);
            } else if (mediaURL.contains("lockerz.com")) {
                linkMedia = new Lockerz(mediaURL);
            } else if (mediaURL.contains("photozou.jp")) {
                linkMedia = new Photozou(mediaURL);
            } else if (mediaURL.contains("nico.ms/im") || mediaURL.contains("seiga.nicovideo.jp/seiga/im")) {
                linkMedia = new NicoSeiga(mediaURL);
            } else if (mediaURL.contains("www.nicovideo.jp/watch") ||
                    (mediaURL.contains("nico.ms/") && !mediaURL.contains("/lv"))) {
                linkMedia = new NicoVideo(mediaURL);
            } else if (mediaURL.contains("www.youtube.com/watch?") || mediaURL.contains("youtu.be/")) {
                linkMedia = new YouTube(mediaURL);
            } else if (mediaURL.contains("/mstr.in/photos/")) {
                linkMedia = new Meshi(mediaURL);
            } else if (mediaURL.contains("/vine.co/v/")) {
                linkMedia = new Vine(mediaURL);
            } else if (mediaURL.endsWith(".jpg") || mediaURL.endsWith(".jpeg") || mediaURL.endsWith(".png")) {
                linkMedia = new SimplePicture(mediaURL);
            }
        }

        instanceQueue.put(mediaURL, linkMedia);

        return linkMedia;
    }
}
