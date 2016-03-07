package shibafu.yukari.media;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Created by Shibafu on 13/12/30.
 */
public abstract class LinkMedia implements Serializable{
    private String browseURL;
    private String mediaURL;
    private String thumbURL;

    public LinkMedia(String browseURL) {
        this.browseURL = browseURL;
        this.mediaURL = expandMediaURL(browseURL);
        this.thumbURL = expandThumbURL(browseURL);
    }

    protected abstract String expandMediaURL(String browseURL);

    protected abstract String expandThumbURL(String browseURL);

    public abstract boolean canPreview();

    @Override
    public String toString() {
        return browseURL;
    }

    public String getBrowseURL() {
        return browseURL;
    }

    public String getThumbURL() {
        return thumbURL;
    }

    public String getMediaURL() {
        return mediaURL;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        LinkMedia linkMedia = (LinkMedia) o;

        if (browseURL != null ? !browseURL.equals(linkMedia.browseURL) : linkMedia.browseURL != null)
            return false;
        if (mediaURL != null ? !mediaURL.equals(linkMedia.mediaURL) : linkMedia.mediaURL != null)
            return false;
        if (thumbURL != null ? !thumbURL.equals(linkMedia.thumbURL) : linkMedia.thumbURL != null)
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = browseURL != null ? browseURL.hashCode() : 0;
        result = 31 * result + (mediaURL != null ? mediaURL.hashCode() : 0);
        result = 31 * result + (thumbURL != null ? thumbURL.hashCode() : 0);
        return result;
    }

    /**
     * Androidはメインスレッドでインターネット接続すると落ちるので、スレッドを切って騙します。
     * <p>
     * <b>こんなもん使うな。設計を改めろ。</b>
     * @param url 接続先URL
     * @param streamFetcher 取得処理
     * @return {@link StreamFetcher#fetch(BufferedReader)} の返り値
     */
    protected String fetchSynced(String url, StreamFetcher streamFetcher) {
        final String[] fetchedUrl = new String[1];
        Thread thread = new Thread(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setReadTimeout(10000);
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                try {
                    fetchedUrl[0] = streamFetcher.fetch(br);
                } finally {
                    br.close();
                    conn.disconnect();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            fetchedUrl[0] = null;
        });
        thread.start();
        try {
            thread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return fetchedUrl[0];
    }

    /**
     * Androidはメインスレッドでインターネット接続すると落ちるので、スレッドを切って騙します。
     * <p>
     * <b>こんなもん使うな。設計を改めろ。</b>
     * @param fetcher 取得処理
     * @return {@link Fetcher#fetch()} の返り値
     */
    protected String fetchSynced(Fetcher fetcher) {
        final String[] fetchedUrl = new String[1];
        Thread thread = new Thread(() -> {
            fetchedUrl[0] = fetcher.fetch();
        });
        thread.start();
        try {
            thread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return fetchedUrl[0];
    }

    /**
     * {@link #fetchSynced(Fetcher)} 処理の実装を定義します。
     */
    protected interface Fetcher {
        /**
         * {@link #fetchSynced(Fetcher)} 内で生成された別スレッドでデータ取得処理を行います。
         * @return {@link #fetchSynced(Fetcher)} の呼び出し元に返す値(URL等)
         */
        String fetch();
    }

    /**
     * {@link #fetchSynced(String, StreamFetcher)} 処理の実装を定義します。
     */
    protected interface StreamFetcher {
        /**
         * {@link #fetchSynced(String, StreamFetcher)} 処理のうち、ストリームからデータを読みだし加工する処理を行います。
         * @param br HttpURLConnection から取得されたストリーム
         * @return {@link #fetchSynced(String, StreamFetcher)} の呼び出し元に返す値(URL等)
         */
        String fetch(BufferedReader br);
    }
}
