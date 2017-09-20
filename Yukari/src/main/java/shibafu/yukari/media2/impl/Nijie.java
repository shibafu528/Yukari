package shibafu.yukari.media2.impl;

import android.support.annotation.NonNull;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import shibafu.yukari.media2.MemoizeMedia;

import java.io.IOException;
import java.util.Map;

public class Nijie extends MemoizeMedia {
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/47.0.2526.106 Safari/537.36";

    /**
     * @param browseUrl メディアの既知のURL
     */
    public Nijie(@NonNull String browseUrl) {
        super(browseUrl);
    }

    @Override
    protected String resolveMediaUrl() throws IOException {
        return findPictureUrl(getBrowseUrl(), false);
    }

    @Override
    protected String resolveThumbnailUrl() throws IOException {
        return findPictureUrl(getBrowseUrl(), true);
    }

    @Override
    public boolean canPreview() {
        return true;
    }

    private static String findPictureUrl(String browseURL, boolean isThumbnail) throws IOException {
        Elements elements = Jsoup.connect(browseURL.replace("http://", "https://"))
                .timeout(10000)
                .userAgent(USER_AGENT)
                .get()
                .select("script[type$=json]");
        if (elements == null || elements.size() < 1) {
            return null;
        }

        Gson gson = new Gson();
        for (Element element : elements) {
            Map<String, ?> m = gson.fromJson(element.html().trim(), new TypeToken<Map<String, ?>>() {}.getType());
            if (m.containsKey("thumbnailUrl")) {
                String thumbnailUrl = m.get("thumbnailUrl").toString();
                if (isThumbnail) {
                    return thumbnailUrl;
                } else {
                    return thumbnailUrl.replaceFirst("nijie\\.info/.*/nijie_picture/", "nijie.info/nijie_picture/");
                }
            }
        }
        return null;
    }
}
