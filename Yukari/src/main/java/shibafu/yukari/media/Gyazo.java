package shibafu.yukari.media;

import android.net.Uri;
import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Created by Shibafu on 13/12/30.
 */
public class Gyazo extends LinkMedia{

    private String mediaUrl;
    private String thumbUrl;

    public Gyazo(String mediaURL) {
        super(mediaURL);
        this.mediaUrl = this.thumbUrl = mediaURL;
        Thread thread = new Thread(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL("https://api.gyazo.com/api/oembed/?url=" + mediaURL).openConnection();
                conn.setReadTimeout(10000);
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                try {
                    String s;
                    while ((s = br.readLine()) != null) {
                        sb.append(s);
                    }
                } finally {
                    br.close();
                    conn.disconnect();
                }

                GyazoOEmbed embed = new Gson().fromJson(sb.toString(), GyazoOEmbed.class);
                this.mediaUrl = embed.url;
                this.thumbUrl = "https://gyazo.com/thumb/" + Uri.parse(embed.url).getLastPathSegment();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        thread.start();
        try {
            thread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected String expandMediaURL(String browseURL) {
        return browseURL;
    }

    @Override
    protected String expandThumbURL(String browseURL) {
        return browseURL;
    }

    @Override
    public String getMediaURL() {
        return mediaUrl;
    }

    @Override
    public String getThumbURL() {
        return thumbUrl;
    }

    @Override
    public boolean canPreview() {
        return true;
    }

    private static class GyazoOEmbed {
        public String url;
        public int width;
        public int height;
    }
}
