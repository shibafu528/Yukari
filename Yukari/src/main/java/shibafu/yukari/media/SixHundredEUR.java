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
public class SixHundredEUR extends LinkMedia {
    private String sourceURL = null;

    public SixHundredEUR(String mediaURL) {
        super(mediaURL);
    }

    private String getSourceURL(final String browseURL) {
        if (sourceURL == null) {
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
                                if (s.contains("name=\"twitter:image:src\"")) {
                                    Pattern pattern = Pattern.compile(".*content=\"(.+\\.png)\".*");
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

            sourceURL = thumbURL[0];
        }
        return sourceURL;
    }

    @Override
    protected String expandMediaURL(String browseURL) {
        return getSourceURL(browseURL);
    }

    @Override
    protected String expandThumbURL(String browseURL) {
        return getSourceURL(browseURL);
    }

    @Override
    public boolean canPreview() {
        return true;
    }
}
