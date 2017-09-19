package shibafu.yukari.media2.impl;

import android.support.annotation.NonNull;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import shibafu.yukari.media2.Media;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Pixiv extends Media {
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/57.0.2987.133 Safari/537.36";
    private static final Pattern URL_PATTERN = Pattern.compile("^https?://(?:www\\.|touch\\.)?pixiv\\.net/member_illust\\.php\\?(?:.*)&?illust_id=(\\d+)(?:&.*)?$");

    /**
     * @param browseUrl メディアの既知のURL
     */
    public Pixiv(@NonNull String browseUrl) {
        super(browseUrl);
    }

    @Override
    public ResolveInfo resolveMedia() throws IOException {
        return resolveInternal();
    }

    @Override
    public ResolveInfo resolveThumbnail() throws IOException {
        return resolveInternal();
    }

    @Override
    public boolean canPreview() {
        return true;
    }

    private ResolveInfo resolveInternal() throws IOException {
        // URLの正規化
        Matcher matcher = URL_PATTERN.matcher(getBrowseUrl());
        if (!matcher.find()) {
            throw new IOException("Not matched : " + getBrowseUrl());
        }
        String pageUrl = String.format("https://www.pixiv.net/member_illust.php?illust_id=%s&mode=medium", matcher.group(1));

        // 実体URLの解決
        Document document = Jsoup.connect(pageUrl)
                .timeout(10000)
                .userAgent(USER_AGENT)
                .get();
        Element imageElement = document.select("div.img-container > a > img").first();
        if (imageElement == null) {
            imageElement = document.select("div.sensored > img").first();
        }
        if (imageElement == null) {
            throw new FileNotFoundException(String.format("browseUrl=%s, pageUrl=%s", getBrowseUrl(), pageUrl));
        }
        String actualImageUrl = imageElement.attr("src");
        if (actualImageUrl == null) {
            throw new FileNotFoundException(String.format("browseUrl=%s, pageUrl=%s", getBrowseUrl(), pageUrl));
        }

        // ストリームのオープン
        HttpURLConnection conn = (HttpURLConnection) new URL(actualImageUrl).openConnection();
        conn.setReadTimeout(10000);
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setRequestProperty("Referer", pageUrl);
        return new ResolveInfo(conn.getInputStream(), conn.getContentLength());
    }
}
