package shibafu.yukari.media;

import android.util.Log;
import nanohttpd.NanoHTTPD;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Properties;

/**
 * Created by Shibafu on 13/12/30.
 */
public class Pixiv extends LinkMedia {

    public Pixiv(String mediaURL) {
        super(mediaURL);
    }

    @Override
    protected String expandMediaURL(String browseURL) {
        ImageMatch matcher = new ImageMatch("^https?://(?:www\\.|touch\\.)?pixiv\\.net/member_illust\\.php\\?(?:.*)&?illust_id=(\\d+)(?:&.*)?$", "http://127.0.0.1:39339/%1");
        return matcher.getFullPageUrl(browseURL);
    }

    @Override
    protected String expandThumbURL(String browseURL) {
        ImageMatch matcher = new ImageMatch("^https?://(?:www\\.|touch\\.)?pixiv\\.net/member_illust\\.php\\?(?:.*)&?illust_id=(\\d+)(?:&.*)?$", "http://127.0.0.1:39339/%1");
        return matcher.getFullPageUrl(browseURL);
    }

    @Override
    public boolean canPreview() {
        return true;
    }

    public static class PixivProxy extends NanoHTTPD {

        public PixivProxy() throws IOException {
            super(39339, new File("."));
        }

        private String resolveUri(String pageUrl) {
            try {
                Document document = Jsoup.connect(pageUrl)
                        .timeout(10000)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/57.0.2987.133 Safari/537.36")
                        .get();
                Element imageElement = document.select("div.img-container > a > img").first();
                if (imageElement == null) {
                    imageElement = document.select("div.sensored > img").first();
                }
                if (imageElement == null) {
                    return null;
                } else {
                    return imageElement.attr("src");
                }
            } catch (IOException ignore) {}
            return null;
        }

        @Override
        public Response serve(String uri, String method, Properties header, Properties parms, Properties files) {
            Log.d("PixivProxy", "Uri: " + uri);
            String illustId = uri.replace("/", "");
            String pageUri = String.format("https://www.pixiv.net/member_illust.php?illust_id=%s&mode=medium", illustId);
            String imageUri = resolveUri(pageUri);
            if (imageUri != null) {
                Log.d("PixivProxy", "Resolved: " + imageUri);
                try {
                    final HttpURLConnection conn = (HttpURLConnection) new URL(imageUri).openConnection();
                    conn.setReadTimeout(10000);
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.2) AppleWebKit/537.22 (KHTML, like Gecko) Chrome/25.0.1364.36 Safari/537.22");
                    conn.setRequestProperty("Referer", pageUri);
                    BufferedInputStream bis = new BufferedInputStream(conn.getInputStream());
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buffer = new byte[4096];
                    int read;
                    while ((read = bis.read(buffer, 0, buffer.length)) != -1) {
                        baos.write(buffer, 0, read);
                    }
                    return new Response(HTTP_OK, conn.getContentType(), new ByteArrayInputStream(baos.toByteArray()));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return new Response(HTTP_NOTFOUND, MIME_PLAINTEXT, "404 not found.");
        }
    }
}
