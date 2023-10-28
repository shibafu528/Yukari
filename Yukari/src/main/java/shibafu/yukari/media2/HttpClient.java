package shibafu.yukari.media2;

import okhttp3.OkHttpClient;

/*package*/ enum HttpClient {
    INSTANCE;

    private final OkHttpClient client;

    HttpClient() {
        client = new OkHttpClient.Builder().build();
    }

    OkHttpClient get() {
        return client;
    }
}
