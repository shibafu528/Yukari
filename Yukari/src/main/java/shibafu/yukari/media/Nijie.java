package shibafu.yukari.media;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.Map;

/**
 * Created by Shibafu on 13/12/30.
 */
public class Nijie extends LinkMedia {

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/47.0.2526.106 Safari/537.36";

    public Nijie(String mediaURL) {
        super(mediaURL);
    }

    @Override
    protected String expandMediaURL(final String browseURL) {
        return fetchSynced(() -> {
            try {
                return findPictureUrl(browseURL, false);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        });
    }

    @Override
    protected String expandThumbURL(final String browseURL) {
        return fetchSynced(() -> {
            try {
                return findPictureUrl(browseURL, true);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        });
    }

    @Override
    public boolean canPreview() {
        return true;
    }

    private String findPictureUrl(String browseURL, boolean isThumbnail) throws IOException {
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
