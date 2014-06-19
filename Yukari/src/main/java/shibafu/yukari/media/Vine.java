package shibafu.yukari.media;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Shibafu on 13/12/30.
 */
public class Vine extends LinkMedia {

    public Vine(String mediaURL) {
        super(mediaURL);
    }

    @Override
    protected String expandMediaURL(final String browseURL) {
        final String[] mediaURL = new String[1];
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    HttpURLConnection conn = (HttpURLConnection) new URL(browseURL).openConnection();
                    conn.setReadTimeout(10000);
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    try {
                        String s;
                        while ((s = br.readLine()) != null) {
                            if (s.contains("property=\"twitter:player:stream\"")) {
                                Pattern pattern = Pattern.compile("content=\"(https?://.+)\"");
                                Matcher m = pattern.matcher(s);
                                if (m.find()) {
                                    mediaURL[0] = m.group(1);
                                    return;
                                }
                            }
                        }
                    } finally {
                        br.close();
                        conn.disconnect();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                mediaURL[0] = null;
            }
        });
        thread.start();
        try {
            thread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return mediaURL[0];
    }

    @Override
    protected String expandThumbURL(final String browseURL) {
        final String[] thumbURL = new String[1];
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    HttpURLConnection conn = (HttpURLConnection) new URL(browseURL).openConnection();
                    conn.setReadTimeout(10000);
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    try {
                        String s;
                        while ((s = br.readLine()) != null) {
                            if (s.contains("property=\"twitter:image\"")) {
                                Pattern pattern = Pattern.compile("content=\"(https?://.+)\"");
                                Matcher m = pattern.matcher(s);
                                if (m.find()) {
                                    thumbURL[0] = m.group(1);
                                    return;
                                }
                            }
                        }
                    } finally {
                        br.close();
                        conn.disconnect();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                thumbURL[0] = null;
            }
        });
        thread.start();
        try {
            thread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return thumbURL[0];
    }

    @Override
    public boolean canPreview() {
        return true;
    }
}
