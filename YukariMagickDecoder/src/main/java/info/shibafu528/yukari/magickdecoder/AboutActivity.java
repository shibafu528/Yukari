package info.shibafu528.yukari.magickdecoder;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebView;

public class AboutActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WebView wv = new WebView(this);
        wv.loadUrl("file:///android_asset/about.html");
        setContentView(wv);
    }
}
