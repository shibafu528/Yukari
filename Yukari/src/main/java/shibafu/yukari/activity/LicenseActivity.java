package shibafu.yukari.activity;


import android.app.Activity;
import android.os.Bundle;
import android.view.MenuItem;
import android.webkit.WebView;

public class LicenseActivity extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		WebView wv = new WebView(this);
		wv.loadUrl("file:///android_asset/about.html");
		setContentView(wv);

        getActionBar().setDisplayHomeAsUpEnabled(true);
	}

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
