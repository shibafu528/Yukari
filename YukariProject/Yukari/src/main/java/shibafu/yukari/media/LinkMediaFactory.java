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
            if (mediaURL.contains("pbs.twimg.com/media/")) {
                linkMedia = new Twimg(mediaURL);
            } else if (mediaURL.contains("pbs.twimg.com/tweet_video/") || mediaURL.contains("video.twimg.com")) {
                linkMedia = new TwitterVideo(mediaURL);
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
            } else if ((mediaURL.contains("www.nicovideo.jp/watch") || mediaURL.contains("nico.ms/"))
                    && (mediaURL.contains("/sm") || mediaURL.contains("/nm"))) {
                linkMedia = new NicoVideo(mediaURL);
            } else if (mediaURL.contains("www.youtube.com/watch?") || mediaURL.contains("youtu.be/")) {
                linkMedia = new YouTube(mediaURL);
            } else if (mediaURL.contains("/mstr.in/photos/")) {
                linkMedia = new Meshi(mediaURL);
            } else if (mediaURL.contains("/vine.co/v/")) {
                linkMedia = new Vine(mediaURL);
            } else if (mediaURL.contains("pixiv.net/member_illust.php")) {
                linkMedia = new Pixiv(mediaURL);
            } else if (mediaURL.contains("img.esx.asia/")) {
                linkMedia = new Esx(mediaURL);
            } else if (mediaURL.contains("twitpic.com/d250g2") || mediaURL.contains("d250g2.com")) {
                linkMedia = new D250g2(mediaURL);
            } else if ((mediaURL.endsWith("/") ? mediaURL.substring(0, mediaURL.length() - 1) : mediaURL).equals("http://totori.dip.jp")) {
                linkMedia = new Totori(mediaURL);
            } else if (mediaURL.contains("gyazo.com")) {
                linkMedia = new Gyazo(mediaURL);
            } else if (mediaURL.contains("sunoho.com/p/i")) {
                linkMedia = new Sunoho(mediaURL);
            } else if (mediaURL.contains("img.ly/")) {
                if (mediaURL.contains("img.ly/show/")) {
                    linkMedia = new SimplePicture(mediaURL);
                } else {
                    linkMedia = new Imgly(mediaURL);
                }
            } else if (mediaURL.contains("twitpic.com")) {
                //f*ck!!!!!!!!!!!!!!!!!!!
                linkMedia = new Twitpic(mediaURL);
            } else if (mediaURL.endsWith(".jpg") || mediaURL.endsWith(".jpeg") || mediaURL.endsWith(".png")) {
                linkMedia = new SimplePicture(mediaURL);
            }
        }

        instanceQueue.put(mediaURL, linkMedia);

        return linkMedia;
    }
}
