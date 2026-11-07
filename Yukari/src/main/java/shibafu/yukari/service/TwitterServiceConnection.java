package shibafu.yukari.service;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

/**
 * Created by shibafu on 14/07/12.
 */
public class TwitterServiceConnection {
    private TwitterService service;
    private boolean serviceBound = false;
    private ServiceConnectionCallback callback;

    public TwitterServiceConnection(ServiceConnectionCallback callback) {
        this.callback = callback;
    }

    public TwitterService getTwitterService() {
        return service;
    }

    public boolean isServiceBound() {
        return serviceBound;
    }

    public void connect(Context context) {
        context.bindService(new Intent(context, TwitterService.class), connection, Context.BIND_AUTO_CREATE);
    }

    public void disconnect(Context context) {
        context.unbindService(connection);
        // FIXME: ここでフラグをfalseにするのが正しいが、正しくしてしまうとTimelineFragment等がクラッシュするようになる
        // serviceBound = false;
    }

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            TwitterService.TweetReceiverBinder binder = (TwitterService.TweetReceiverBinder) service;
            TwitterServiceConnection.this.service = binder.getService();
            serviceBound = true;
            callback.onServiceConnected();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
            callback.onServiceDisconnected();
        }
    };

    public static interface ServiceConnectionCallback {
        void onServiceConnected();
        void onServiceDisconnected();
    }
}
