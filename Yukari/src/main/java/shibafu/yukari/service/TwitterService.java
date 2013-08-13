package shibafu.yukari.service;

import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import shibafu.yukari.R;
import shibafu.yukari.common.HashCache;
import shibafu.yukari.twitter.AuthUserRecord;
import shibafu.yukari.twitter.StreamUser;
import shibafu.yukari.twitter.TwitterUtil;
import twitter4j.DirectMessage;
import twitter4j.HashtagEntity;
import twitter4j.Status;
import twitter4j.StatusUpdate;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.User;
import twitter4j.UserMentionEntity;
import twitter4j.conf.Configuration;
import twitter4j.conf.ConfigurationBuilder;
import twitter4j.media.ImageUpload;
import twitter4j.media.ImageUploadFactory;
import twitter4j.media.MediaProvider;

/**
 * Created by Shibafu on 13/08/01.
 */
public class TwitterService extends Service{
    private static final String LOG_TAG = "TwitterService";
    //Binder
    private final IBinder binder = new TweetReceiverBinder();
    public class TweetReceiverBinder extends Binder {
        public TwitterService getService() {
            return TwitterService.this;
        }
    }

    //キャッシュ系
    private HashCache hashCache;

    //システムサービス系
    private NotificationManager notificationManager;
    private static final int NOTIF_FAVED = 1;
    private static final int NOTIF_REPLY = 2;
    private static final int NOTIF_RETWEET = 3;

    //Twitter通信系
    private Twitter twitter;
    private List<AuthUserRecord> users = new ArrayList<AuthUserRecord>();
    private List<StreamUser> streamUsers = new ArrayList<StreamUser>();
    private StreamUser.StreamListener listener = new StreamUser.StreamListener() {

        @Override
        public void onFavorite(AuthUserRecord from, User user, User user2, Status status) {
            if (from.NumericId == user.getId())
                return;

            NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext());
            builder.setSmallIcon(R.drawable.ic_stat_favorite);
            builder.setContentTitle("Faved by @" + user.getScreenName());
            builder.setContentText(status.getUser().getScreenName() + ": " + status.getText());
            builder.setTicker("ふぁぼられ : @" + user.getScreenName());
            builder.setSound(Uri.parse("android.resource://shibafu.yukari/raw/se_fav"), AudioManager.STREAM_NOTIFICATION);
            builder.setAutoCancel(true);

            notificationManager.notify(NOTIF_FAVED, builder.build());
        }

        @Override
        public void onUnfavorite(AuthUserRecord from, User user, User user2, Status status) {

        }

        @Override
        public void onFollow(AuthUserRecord from, User user, User user2) {

        }

        @Override
        public void onDirectMessage(AuthUserRecord from, DirectMessage directMessage) {
            for (StatusListener sl : statusListeners) {
                sl.onDirectMessage(from, directMessage);
            }
        }

        @Override
        public void onBlock(AuthUserRecord from, User user, User user2) {

        }

        @Override
        public void onUnblock(AuthUserRecord from, User user, User user2) {

        }

        @Override
        public void onStatus(AuthUserRecord from, Status status) {
            Log.d(LOG_TAG, "onStatus(Registed Listener " + statusListeners.size() + "): @" + status.getUser().getScreenName() + " " + status.getText());
            for (StatusListener sl : statusListeners) {
                sl.onStatus(from, status);
            }

            //TODO: 自分自身のアカウントからは除外
            if (status.isRetweet() && status.getRetweetedStatus().getUser().getId() == from.NumericId) {
                NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext());
                builder.setSmallIcon(R.drawable.ic_stat_retweet);
                builder.setContentTitle("Retweeted by @" + status.getUser().getScreenName());
                builder.setContentText(status.getUser().getScreenName() + ": " + status.getText());
                builder.setTicker("RTされました : @" + status.getUser().getScreenName());
                builder.setSound(Uri.parse("android.resource://shibafu.yukari/raw/se_rt"), AudioManager.STREAM_NOTIFICATION);
                builder.setAutoCancel(true);

                notificationManager.notify(NOTIF_RETWEET, builder.build());
            }
            else {
                UserMentionEntity[] userMentionEntities = status.getUserMentionEntities();
                for (UserMentionEntity ume : userMentionEntities) {
                    if (ume.getId() == from.NumericId) {
                        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext());
                        builder.setSmallIcon(R.drawable.ic_stat_reply);
                        builder.setContentTitle("Reply from @" + status.getUser().getScreenName());
                        builder.setContentText(status.getUser().getScreenName() + ": " + status.getText());
                        builder.setTicker("リプライ : @" + status.getUser().getScreenName());
                        builder.setSound(Uri.parse("android.resource://shibafu.yukari/raw/se_reply"), AudioManager.STREAM_NOTIFICATION);
                        builder.setAutoCancel(true);

                        notificationManager.notify(NOTIF_REPLY, builder.build());
                    }
                }
            }

            HashtagEntity[] hashtagEntities = status.getHashtagEntities();
            for (HashtagEntity he : hashtagEntities) {
                hashCache.put("#" + he.getText());
            }
        }
    };
    public interface StatusListener {
        public void onStatus(AuthUserRecord from, Status status);
        public void onDirectMessage(AuthUserRecord from, DirectMessage directMessage);
    }
    private List<StatusListener> statusListeners = new ArrayList<StatusListener>();

    //Handler
    private Handler handler;

    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(LOG_TAG, "onCreate");
        handler = new Handler();
        twitter = TwitterUtil.getTwitterInstance(this);
        reloadUsers();
        hashCache = new HashCache(this);
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        Toast.makeText(this, "Yukari Serviceを起動しました", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(LOG_TAG, "onDestroy");
        for (StreamUser su : streamUsers) {
            su.stop();
        }
        streamUsers.clear();
        streamUsers = null;
        statusListeners.clear();
        statusListeners = null;
        listener = null;
        twitter = null;
        users = null;
        hashCache.save(this);

        Log.d(LOG_TAG, "onDestroy completed.");
        Toast.makeText(this, "Yukari Serviceを停止しました", Toast.LENGTH_SHORT).show();
    }

    public void reloadUsers() {
        List<AuthUserRecord> newestList = Arrays.asList(TwitterUtil.loadUserRecords(this));
        //消えたレコードの削除処理
        List<AuthUserRecord> removeList = new ArrayList<AuthUserRecord>();
        for (AuthUserRecord aur : users) {
            if (!newestList.contains(aur)) {
                StreamUser remove = null;
                for (StreamUser su : streamUsers) {
                    if (su.getUserRecord().equals(aur)) {
                        su.stop();
                        remove = su;
                        break;
                    }
                }
                if (remove != null) {
                    statusListeners.remove(remove);
                }
                removeList.add(aur);
                Log.d(LOG_TAG, "Remove user: @" + aur.ScreenName);
            }
        }
        users.removeAll(removeList);
        //新しいレコードの登録
        for (AuthUserRecord aur : newestList) {
            if (!users.contains(aur)) {
                users.add(aur);
                StreamUser su = new StreamUser(this, aur);
                su.setListener(listener);
                streamUsers.add(su);
                su.start();
                Log.d(LOG_TAG, "Add user: @" + aur.ScreenName);
            }
        }
        Log.d(LOG_TAG, "Reloaded users. User=" + users.size() + ", StreamUsers=" + statusListeners.size());
    }

    public void addStatusListener(StatusListener l) {
        if (!statusListeners.contains(l))
            statusListeners.add(l);
    }

    public void removeStatusListener(StatusListener l) {
        if (statusListeners.contains(l))
            statusListeners.remove(l);
    }

    public List<AuthUserRecord> getUsers() {
        return users;
    }

    private void showToast(final String text) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), text, Toast.LENGTH_SHORT).show();
            }
        });
    }

    public ConfigurationBuilder getBuilder() {
        ConfigurationBuilder builder = new ConfigurationBuilder();
        builder.setOAuthConsumerKey(getString(R.string.twitter_consumer_key));
        builder.setOAuthConsumerSecret(getString(R.string.twitter_consumer_secret));
        return builder;
    }

    public String[] getHashCache() {
        return hashCache.getAll().toArray(new String[0]);
    }

    public Twitter getTwitter() {
        return twitter;
    }

    //<editor-fold desc="投稿操作系">
    public void postTweet(AuthUserRecord user, StatusUpdate status) throws TwitterException {
        if (user != null) {
            twitter.setOAuthAccessToken(user.getAccessToken());
            twitter.updateStatus(status);
        }
        else {
            for (AuthUserRecord aur : users) {
                twitter.setOAuthAccessToken(aur.getAccessToken());
                twitter.updateStatus(status);
            }
        }
    }

    public void postTweet(AuthUserRecord user, StatusUpdate status, File[] media) throws  TwitterException {
        ConfigurationBuilder builder = getBuilder();
        if (user != null) {
            builder.setOAuthAccessToken(user.getAccessToken().getToken());
            builder.setOAuthAccessTokenSecret(user.getAccessToken().getTokenSecret());

            Configuration conf = builder.build();

            MediaProvider service = MediaProvider.TWITTER;
            StringBuilder urls = new StringBuilder(status.getStatus());
            for (File m : media) {
                ImageUpload upload = new ImageUploadFactory(conf).getInstance(MediaProvider.TWITTER);
                String url = upload.upload(m, status.getStatus());
                if (service.getClass() != MediaProvider.TWITTER.getClass()) {
                    urls.append(" ");
                    urls.append(url);
                }
                else {
                    return;
                }
            }
            postTweet(user, new StatusUpdate(urls.toString()));
        }
        else {
            for (AuthUserRecord aur : users) {
                builder.setOAuthAccessToken(aur.getAccessToken().getToken());
                builder.setOAuthAccessTokenSecret(aur.getAccessToken().getTokenSecret());

                Configuration conf = builder.build();

                MediaProvider service = MediaProvider.TWITTER;
                StringBuilder urls = new StringBuilder(status.getStatus());
                boolean skip = false;
                for (File m : media) {
                    ImageUpload upload = new ImageUploadFactory(conf).getInstance(MediaProvider.TWITTER);
                    String url = upload.upload(m, status.getStatus());
                    if (service.getClass() != MediaProvider.TWITTER.getClass()) {
                        urls.append(" ");
                        urls.append(url);
                    }
                    else {
                        skip = true;
                        break;
                    }
                }
                if (skip) continue;
                postTweet(user, new StatusUpdate(urls.toString()));
            }
        }
    }

    public void postTweet(AuthUserRecord user, StatusUpdate status, InputStream[] media) throws  TwitterException {
        ConfigurationBuilder builder = getBuilder();
        if (user != null) {
            builder.setOAuthAccessToken(user.getAccessToken().getToken());
            builder.setOAuthAccessTokenSecret(user.getAccessToken().getTokenSecret());

            Configuration conf = builder.build();

            MediaProvider service = MediaProvider.TWITTER;
            StringBuilder urls = new StringBuilder(status.getStatus());
            for (InputStream m : media) {
                ImageUpload upload = new ImageUploadFactory(conf).getInstance(MediaProvider.TWITTER);
                String url = upload.upload("image", m, status.getStatus());
                if (service.getClass() != MediaProvider.TWITTER.getClass()) {
                    urls.append(" ");
                    urls.append(url);
                }
                else {
                    return;
                }
            }
            postTweet(user, new StatusUpdate(urls.toString()));
        }
        else {
            for (AuthUserRecord aur : users) {
                builder.setOAuthAccessToken(aur.getAccessToken().getToken());
                builder.setOAuthAccessTokenSecret(aur.getAccessToken().getTokenSecret());

                Configuration conf = builder.build();

                MediaProvider service = MediaProvider.TWITTER;
                StringBuilder urls = new StringBuilder(status.getStatus());
                boolean skip = false;
                for (InputStream m : media) {
                    ImageUpload upload = new ImageUploadFactory(conf).getInstance(MediaProvider.TWITTER);
                    String url = upload.upload("image", m, status.getStatus());
                    if (service.getClass() != MediaProvider.TWITTER.getClass()) {
                        urls.append(" ");
                        urls.append(url);
                    }
                    else {
                        skip = true;
                        break;
                    }
                }
                if (skip) continue;
                postTweet(user, new StatusUpdate(urls.toString()));
            }
        }
    }

    public void sendDirectMessage(String to, AuthUserRecord from, String message) throws TwitterException {
        if (from == null) {
            throw new IllegalArgumentException("送信元アカウントが指定されていません");
        }
        twitter.setOAuthAccessToken(from.getAccessToken());
        twitter.sendDirectMessage(to, message);
    }

    public void retweetStatus(AuthUserRecord user, long id){
        if (user != null) {
            twitter.setOAuthAccessToken(user.getAccessToken());
            try {
                twitter.retweetStatus(id);
                showToast("RTしました");
            } catch (TwitterException e) {
                e.printStackTrace();
                showToast("RTに失敗しました");
            }
        }
        else {
            int success = 0, failed = 0;
            for (AuthUserRecord aur : users) {
                twitter.setOAuthAccessToken(aur.getAccessToken());
                try {
                    twitter.retweetStatus(id);
                } catch (TwitterException e) {
                    e.printStackTrace();
                }
            }
            showToast("RT成功: " + success + ((failed > 0)? " / RT失敗: " + failed: ""));
        }
    }

    public void createFavorite(AuthUserRecord user, long id){
        if (user != null) {
            twitter.setOAuthAccessToken(user.getAccessToken());
            try {
                twitter.createFavorite(id);
                showToast("ふぁぼりました");
            } catch (TwitterException e) {
                e.printStackTrace();
                showToast("ふぁぼれませんでした");
            }
        }
        else {
            int success = 0, failed = 0;
            for (AuthUserRecord aur : users) {
                twitter.setOAuthAccessToken(aur.getAccessToken());
                try {
                    twitter.createFavorite(id);
                    ++success;
                } catch (TwitterException e) {
                    e.printStackTrace();
                    ++failed;
                }
            }
            showToast("ふぁぼ成功: " + success + ((failed > 0)? " / ふぁぼ失敗: " + failed: ""));
        }
    }
    //</editor-fold>

    //<editor-fold desc="URL/Quote生成">
    public static String getTweetURL(Status status) {
        if (status.isRetweet()) {
            status = status.getRetweetedStatus();
        }
        StringBuilder sb = new StringBuilder();
        sb.append("http://twitter.com/");
        sb.append(status.getUser().getScreenName());
        sb.append("/status/");
        sb.append(status.getId());
        return sb.toString();
    }

    public static String createSTOT(Status status) {
        if (status.isRetweet()) {
            status = status.getRetweetedStatus();
        }
        StringBuilder sb = new StringBuilder();
        sb.append(status.getUser().getScreenName());
        sb.append(":");
        sb.append(status.getText());
        sb.append(" [");
        sb.append(getTweetURL(status));
        sb.append("]");
        return sb.toString();
    }

    public static String createQuotedRT(Status status) {
        if (status.isRetweet()) {
            status = status.getRetweetedStatus();
        }
        StringBuilder sb = new StringBuilder(" RT @");
        sb.append(status.getUser().getScreenName());
        sb.append(": ");
        sb.append(status.getText());
        return sb.toString();
    }

    public static String createQT(Status status) {
        if (status.isRetweet()) {
            status = status.getRetweetedStatus();
        }
        StringBuilder sb = new StringBuilder(" QT @");
        sb.append(status.getUser().getScreenName());
        sb.append(": ");
        sb.append(status.getText());
        return sb.toString();
    }

    public static String createQuote(Status status) {
        if (status.isRetweet()) {
            status = status.getRetweetedStatus();
        }
        StringBuilder sb = new StringBuilder(" \"@");
        sb.append(status.getUser().getScreenName());
        sb.append(": ");
        sb.append(status.getText());
        sb.append("\"");
        return sb.toString();
    }
    //</editor-fold>

    public boolean isMyTweet(Status status) {
        for (AuthUserRecord aur : users) {
            if (status.getUser().getId() == aur.NumericId) {
                return true;
            }
        }
        return false;
    }
}
