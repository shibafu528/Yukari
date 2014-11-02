package shibafu.yukari.media;

import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import nanohttpd.NanoHTTPD;

/**
 * Created by Shibafu on 13/12/30.
 */
public class Pixiv extends LinkMedia {

    public Pixiv(String mediaURL) {
        super(mediaURL);
    }

    @Override
    protected String expandMediaURL(String browseURL) {
        ImageMatch matcher = new ImageMatch("^http:\\/\\/(?:www\\.)?pixiv\\.net\\/member_illust\\.php\\?(?:.*)&?illust_id=(\\d+)(?:&.*)?$", "http://127.0.0.1:39339/%1");
        return matcher.getFullPageUrl(browseURL);
    }

    @Override
    protected String expandThumbURL(String browseURL) {
        ImageMatch matcher = new ImageMatch("^http:\\/\\/(?:www\\.)?pixiv\\.net\\/member_illust\\.php\\?(?:.*)&?illust_id=(\\d+)(?:&.*)?$", "http://127.0.0.1:39339/%1");
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

        private String resolveUri(String pageUrl, String illustId) {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(pageUrl).openConnection();
                conn.setReadTimeout(10000);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.2) AppleWebKit/537.22 (KHTML, like Gecko) Chrome/25.0.1364.36 Safari/537.22");
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                try {
                    String s;
                    Pattern pattern = Pattern.compile("(http://i[0-9]\\.pixiv\\.net/(?:img[0-9]{2,3}/img/[a-zA-Z0-9\\-_]+/|c/600x600/img-master/img/\\d{4}/(?:\\d{2}/){5})"+illustId+"(?:_m|_p0_master1200)\\.(?:jpg|png))");
                    while ((s = br.readLine()) != null) {
                        Matcher m = pattern.matcher(s);
                        if (m.find()) {
                            return m.group(1);
                        }
                    }
                } finally {
                    br.close();
                    conn.disconnect();
                }
            } catch (IOException ignore) {}
            return null;
        }

        @Override
        public Response serve(String uri, String method, Properties header, Properties parms, Properties files) {
            Log.d("PixivProxy", "Uri: " + uri);
            String illustId = uri.replace("/", "");
            String pageUri = String.format("http://www.pixiv.net/member_illust.php?illust_id=%s&mode=medium", illustId);
            String imageUri = resolveUri(pageUri, illustId);
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
