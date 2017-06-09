package shibafu.yukari.activity;

import android.os.Bundle;
import android.os.Handler;
import android.view.MenuItem;
import android.widget.EditText;
import butterknife.BindView;
import butterknife.ButterKnife;
import shibafu.yukari.R;
import shibafu.yukari.activity.base.ActionBarYukariBase;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by shibafu on 2016/05/04.
 */
public class PluggaloidOutputActivity extends ActionBarYukariBase {
    private Handler handler = new Handler();
    private Timer timer;

    @BindView(R.id.editText)
    EditText logView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pluggaloid_output);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        ButterKnife.bind(this);
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
    protected void onResume() {
        super.onResume();

        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (isTwitterServiceBound() && getTwitterService() != null) {
                    handler.post(() -> {
                        String s = getTwitterService().getmRubyStdOut().toString();
                        if (logView.getText().length() != s.length()) {
                            logView.setText(s);
                            logView.scrollTo(65536, 0);
                        }
                    });
                }
            }
        }, 0, 1000);
    }

    @Override
    protected void onPause() {
        super.onPause();

        timer.cancel();
        timer = null;
    }

    @Override
    public void onServiceConnected() {}

    @Override
    public void onServiceDisconnected() {}
}
