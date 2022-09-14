package shibafu.yukari.activity;

import android.os.Bundle;
import android.os.Handler;
import android.view.MenuItem;
import shibafu.yukari.activity.base.ActionBarYukariBase;
import shibafu.yukari.databinding.ActivityPluggaloidOutputBinding;
import shibafu.yukari.plugin.Pluggaloid;
import shibafu.yukari.service.TwitterService;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by shibafu on 2016/05/04.
 */
public class PluggaloidOutputActivity extends ActionBarYukariBase {
    private Handler handler = new Handler();
    private Timer timer;

    private ActivityPluggaloidOutputBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPluggaloidOutputBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
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
    protected void onResume() {
        super.onResume();

        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                TwitterService twitterService = getTwitterService();
                if (isTwitterServiceBound() && twitterService != null) {
                    handler.post(() -> {
                        Pluggaloid pluggaloid = twitterService.getPluggaloid();
                        if (pluggaloid == null) {
                            return;
                        }
                        String s = pluggaloid.getLogger().toString();
                        if (binding.editText.getText().length() != s.length()) {
                            binding.editText.setText(s);
                            binding.editText.scrollTo(65536, 0);
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
