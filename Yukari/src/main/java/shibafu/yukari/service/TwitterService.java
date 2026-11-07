package shibafu.yukari.service;

import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.BitmapFactory;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.collection.ArrayMap;
import androidx.collection.LongSparseArray;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.util.Log;
import android.widget.Toast;

import shibafu.yukari.R;
import shibafu.yukari.activity.MainActivity;
import shibafu.yukari.common.HashCache;
import shibafu.yukari.common.Suppressor;
import shibafu.yukari.core.App;
import shibafu.yukari.database.AccountManager;
import shibafu.yukari.database.CentralDatabase;
import shibafu.yukari.database.Provider;
import shibafu.yukari.database.UserExtras;
import shibafu.yukari.database.UserExtrasManager;
import shibafu.yukari.linkage.ApiCollectionProvider;
import shibafu.yukari.linkage.ProviderApi;
import shibafu.yukari.linkage.ProviderStream;
import shibafu.yukari.linkage.StatusLoader;
import shibafu.yukari.linkage.StreamCollectionProvider;
import shibafu.yukari.linkage.TimelineHub;
import shibafu.yukari.linkage.TimelineHubProvider;
import shibafu.yukari.mastodon.DefaultVisibilityCache;
import shibafu.yukari.mastodon.MastodonStream;
import shibafu.yukari.database.AuthUserRecord;
import shibafu.yukari.twitter.MissingTwitterInstanceException;
import shibafu.yukari.twitter.TwitterProvider;
import shibafu.yukari.twitter.TwitterStream;
import twitter4j.Twitter;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Shibafu on 13/08/01.
 */
public class TwitterService extends Service implements ApiCollectionProvider, StreamCollectionProvider, AccountManager, UserExtrasManager, TwitterProvider, TimelineHubProvider {
    private static final String LOG_TAG = "TwitterService";

    //Binder
    private final IBinder binder = new TweetReceiverBinder();

    public class TweetReceiverBinder extends Binder {
        public TwitterService getService() {
            return TwitterService.this;
        }
    }

    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), getString(R.string.notification_channel_id_core_service))
                .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher))
                .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
                .setContentTitle(getString(R.string.app_name))
                .setContentText("Yukariを実行中です")
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setShowWhen(false)
                .setOngoing(true)
                .setLocalOnly(true)
                .setColor(ResourcesCompat.getColor(getResources(), R.color.key_color, null))
                .setContentIntent(PendingIntent.getActivity(getApplicationContext(), 0, new Intent(getApplicationContext(), MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        startForeground(R.string.app_name, builder.build());
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(LOG_TAG, "onCreate");
        Log.d(LOG_TAG, "onCreate completed.");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(LOG_TAG, "onDestroy");

        stopForeground(true);

        Log.d(LOG_TAG, "onDestroy completed.");
    }

    /**
     * @deprecated Use {@link App#getStatusLoader()} instead.
     */
    @Deprecated
    public StatusLoader getStatusLoader() {
        return App.getInstance(this).getStatusLoader();
    }

    /**
     * @deprecated Use {@link App#getTimelineHub()} instead.
     */
    @Deprecated
    public TimelineHub getTimelineHub() {
        return App.getInstance(this).getTimelineHub();
    }

    /**
     * @deprecated Use {@link App#getDatabase()} instead.
     */
    @Deprecated
    public CentralDatabase getDatabase() {
        return App.getInstance(this).getDatabase();
    }

    /**
     * @deprecated Use {@link App#getSuppressor()} instead.
     */
    @Deprecated
    public Suppressor getSuppressor() {
        return App.getInstance(this).getSuppressor();
    }

    /**
     * @deprecated Use {@link App#getHashCache()} instead.
     */
    @Deprecated
    public HashCache getHashCache() {
        return App.getInstance(this).getHashCache();
    }

    /**
     * @deprecated Use {@link App#getDefaultVisibilityCache()}
     */
    @Deprecated
    public DefaultVisibilityCache getDefaultVisibilityCache() {
        return App.getInstance(this).getDefaultVisibilityCache();
    }

    /**
     * @deprecated Use {@link App#getAccountManager()} instead.
     */
    @Deprecated
    private AccountManager getAccountManager() {
        return App.getInstance(this).getAccountManager();
    }

    /**
     * @deprecated Use {@link App#getUserExtrasManager()} instead.
     */
    @Deprecated
    private UserExtrasManager getUserExtrasManager() {
        return App.getInstance(this).getUserExtrasManager();
    }

    //<editor-fold desc="AccountManager delegates">
    @Override
    @Deprecated
    public void reloadUsers() {
        getAccountManager().reloadUsers();
    }

    @Override
    @NonNull
    @Deprecated
    public List<AuthUserRecord> getUsers() {
        return getAccountManager().getUsers();
    }

    @Override
    @Nullable
    @Deprecated
    public AuthUserRecord getPrimaryUser() {
        return getAccountManager().getPrimaryUser();
    }

    @Override
    @Deprecated
    public void setPrimaryUser(long id) {
        getAccountManager().setPrimaryUser(id);
    }

    @Override
    @Deprecated
    public ArrayList<AuthUserRecord> getWriterUsers() {
        return getAccountManager().getWriterUsers();
    }

    @Override
    @Deprecated
    public void setWriterUsers(List<AuthUserRecord> writers) {
        getAccountManager().setWriterUsers(writers);
    }

    @Override
    @Deprecated
    public void setUserColor(long id, int color) {
        getAccountManager().setUserColor(id, color);
    }

    @Override
    @Deprecated
    public void storeUsers() {
        getAccountManager().storeUsers();
    }

    @Override
    @Deprecated
    public void deleteUser(long id) {
        getAccountManager().deleteUser(id);
    }

    @Override
    @Nullable
    @Deprecated
    public AuthUserRecord findPreferredUser(int apiType) {
        return getAccountManager().findPreferredUser(apiType);
    }
    //</editor-fold>

    //<editor-fold desc="UserExtrasManager delegates">
    @Override
    @Deprecated
    public void setColor(String url, int color) {
        getUserExtrasManager().setColor(url, color);
    }

    @Override
    @Deprecated
    public void setPriority(String url, AuthUserRecord userRecord) {
        getUserExtrasManager().setPriority(url, userRecord);
    }

    @Override
    @Nullable
    @Deprecated
    public AuthUserRecord getPriority(String url) {
        return getUserExtrasManager().getPriority(url);
    }

    @Override
    @Deprecated
    public List<UserExtras> getUserExtras() {
        return getUserExtrasManager().getUserExtras();
    }
    //</editor-fold>

    //<editor-fold desc="通信クライアントインスタンスの取得 (Legacy)">
    /**
     * 指定のアカウントの認証情報を設定した {@link Twitter} インスタンスを取得します。結果はアカウントID毎にキャッシュされます。
     * @param userRecord 認証情報。ここに null を指定すると、AccessTokenの設定されていないインスタンスを取得できます。
     * @return キーとトークンの設定された {@link Twitter} インスタンス。引数 userRecord が null の場合、AccessTokenは未設定。
     */
    @Override
    @Nullable
    @Deprecated
    public Twitter getTwitter(@Nullable AuthUserRecord userRecord) {
        return (Twitter) getProviderApi(Provider.API_TWITTER).getApiClient(userRecord);
    }

    /**
     * 指定のアカウントの認証情報を設定した {@link Twitter} インスタンスを取得します。
     * {@link #getTwitter(AuthUserRecord)} との違いは、こちらはインスタンスの取得に失敗した際、例外をスローすることです。
     * @param userRecord 認証情報。ここに null を指定すると、AccessTokenの設定されていないインスタンスを取得できます。
     * @return キーとトークンの設定された {@link Twitter} インスタンス。引数 userRecord が null の場合、AccessTokenは未設定。
     */
    @Override
    @NonNull
    @Deprecated
    public Twitter getTwitterOrThrow(@Nullable AuthUserRecord userRecord) throws MissingTwitterInstanceException {
        Twitter twitter = getTwitter(userRecord);
        if (twitter == null) {
            throw new MissingTwitterInstanceException("Twitter インスタンスの取得エラー");
        }
        return twitter;
    }
    //</editor-fold>

    //<editor-fold desc="Provider API">
    /**
     * 指定のアカウントに対応したAPIインスタンスを取得します。
     * @param userRecord 認証情報。
     * @return APIインスタンス。アカウントが所属するサービスに対応したものが返されます。
     */
    @Override
    @Nullable
    @Deprecated
    public ProviderApi getProviderApi(@NonNull AuthUserRecord userRecord) {
        return App.getInstance(this).getProviderApi(userRecord);
    }

    /**
     * 指定のAPI形式に対応したAPIインスタンスを取得します。
     * 対応するAPIインスタンスが定義されていない場合、例外をスローします。
     * @param apiType API形式。{@link Provider} 内の定数を参照。
     * @return APIインスタンス。
     * @throws UnsupportedOperationException 対応するAPIインスタンスが定義されていない場合にスロー
     */
    @Override
    @NonNull
    @Deprecated
    public ProviderApi getProviderApi(int apiType) {
        return App.getInstance(this).getProviderApi(apiType);
    }
    //</editor-fold>

    //<editor-fold desc="Provider Stream API">
    /**
     * @deprecated Use {@link App#getProviderStream(AuthUserRecord)} instead.
     */
    @Override
    @Nullable
    @Deprecated
    public ProviderStream getProviderStream(@NonNull AuthUserRecord userRecord) {
        return App.getInstance(this).getProviderStream(userRecord);
    }

    /**
     * @deprecated Use {@link App#getProviderStream(int)} instead.
     */
    @Override
    @NonNull
    @Deprecated
    public ProviderStream getProviderStream(int apiType) {
        return App.getInstance(this).getProviderStream(apiType);
    }

    /**
     * @deprecated Use {@link App#getProviderStreams()} instead.
     */
    @Override
    @Deprecated
    public ProviderStream[] getProviderStreams() {
        return App.getInstance(this).getProviderStreams();
    }
    //</editor-fold>
}
