package shibafu.yukari.service;

import android.app.IntentService;
import android.content.Intent;

/**
 * Created by shibafu on 14/03/04.
 */
public class CacheCleanerService extends IntentService {

    public CacheCleanerService() {
        super("CacheCleanerService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        //TODO: 古いファイルがどれくらいあるか確認する→消す
    }
}
