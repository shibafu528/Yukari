package shibafu.yukari.activity;


import android.os.Bundle;
import android.view.MenuItem;
import android.webkit.WebView;

import shibafu.yukari.activity.base.ActionBarYukariBase;

public class LicenseActivity extends ActionBarYukariBase {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		WebView wv = new WebView(this);
		wv.loadUrl("file:///android_asset/about.html");
		setContentView(wv);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
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

    @Override
    public void onServiceConnected() {}

    @Override
    public void onServiceDisconnected() {}
}
