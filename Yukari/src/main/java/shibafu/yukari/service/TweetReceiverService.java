package shibafu.yukari.service;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import shibafu.yukari.twitter.AuthUserRecord;
import shibafu.yukari.twitter.StreamUser;
import shibafu.yukari.twitter.TwitterUtil;
import twitter4j.DirectMessage;
import twitter4j.Status;
import twitter4j.Twitter;
import twitter4j.User;

/**
 * Created by Shibafu on 13/08/01.
 */
public class TweetReceiverService extends Service{
    //Binder
    private final IBinder binder = new TweetReceiverBinder();
    public class TweetReceiverBinder extends Binder {
        public TweetReceiverService getService() {
            return TweetReceiverService.this;
        }
    }

    //Twitter通信系
    private Twitter twitter;
    private List<AuthUserRecord> users = new ArrayList<AuthUserRecord>();
    private List<StreamUser> streamUsers = new ArrayList<StreamUser>();
    private StreamUser.StreamListener listener = new StreamUser.StreamListener() {
        @Override
        public void onFavorite(AuthUserRecord from, User user, User user2, Status status) {

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
            Log.d("TweetReceiverService", "onStatus: @" + status.getUser().getScreenName() + " " + status.getText());
            for (StatusListener sl : statusListeners) {
                sl.onStatus(from, status);
            }
        }
    };
    public interface StatusListener {
        public void onStatus(AuthUserRecord from, Status status);
        public void onDirectMessage(AuthUserRecord from, DirectMessage directMessage);
    }
    public List<StatusListener> statusListeners = new ArrayList<StatusListener>();

    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        twitter = TwitterUtil.getTwitterInstance(this);
        reloadUsers();

        Log.d("TweetReceiverService", "onCreate");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
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

        Log.d("TweetReceiverService", "onDestroy");
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
            }
        }
    }

    public void addStatusListener(StatusListener l) {
        if (!statusListeners.contains(l))
            statusListeners.add(l);
    }

    public void removeStatusListener(StatusListener l) {
        if (statusListeners.contains(l))
            statusListeners.remove(l);
    }
}
