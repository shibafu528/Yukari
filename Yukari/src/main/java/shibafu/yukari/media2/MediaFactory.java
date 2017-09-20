package shibafu.yukari.media2;

import android.util.Log;
import shibafu.yukari.media2.impl.*;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class MediaFactory {
    private static Map<String, Media> instanceQueue = Collections.synchronizedMap(new LinkedHashMap<String, Media>() {
        @Override
        protected boolean removeEldestEntry(Entry eldest) {
            return size() > 32;
        }
    });

    public static Media newInstance(String browseUrl) {
        if (browseUrl == null) return null;

        Media media = instanceQueue.get(browseUrl);

        if (media == null) {
            if (browseUrl.contains("pbs.twimg.com/media/")) {
                media = new Twimg(browseUrl);
            } else if (browseUrl.contains("pbs.twimg.com/profile_")) {
                media = new SimplePicture(browseUrl);
            } else if (browseUrl.contains("pbs.twimg.com/tweet_video/") || browseUrl.contains("video.twimg.com")) {
                media = new TwitterVideo(browseUrl);
            } else if (browseUrl.contains("pixiv.net/member_illust.php")) {
                media = new Pixiv(browseUrl);
            } else {
                String lc = browseUrl.toLowerCase();
                if (lc.endsWith(".jpg") || lc.endsWith(".jpeg") || lc.endsWith(".png") || lc.endsWith(".gif")) {
                    media = new SimplePicture(browseUrl);
                }
            }
        }

        if (media == null) {
            Log.d(MediaFactory.class.getSimpleName(), "Resolve failed : " + browseUrl);
        }

        instanceQueue.put(browseUrl, media);

        return media;
    }
}
