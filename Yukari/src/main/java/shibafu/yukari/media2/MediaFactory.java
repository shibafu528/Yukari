package shibafu.yukari.media2;

import java.util.LinkedHashMap;
import java.util.Map;

public class MediaFactory {
    private static Map<String, Media> instanceQueue = new LinkedHashMap<String, Media>() {
        @Override
        protected boolean removeEldestEntry(Entry eldest) {
            return size() > 32;
        }
    };

    public static Media newInstance(String browseUrl) {
        Media media = instanceQueue.get(browseUrl);

        if (media == null) {
            if (browseUrl.contains("pbs.twimg.com/media/")) {
                media = new Twimg(browseUrl);
            } else if (browseUrl.contains("pixiv.net/member_illust.php")) {
                media = new Pixiv(browseUrl);
            } else if (browseUrl.endsWith(".jpg") || browseUrl.endsWith(".jpeg") || browseUrl.endsWith(".png")) {
                media = new SimplePicture(browseUrl);
            }
        }

        instanceQueue.put(browseUrl, media);

        return media;
    }
}
