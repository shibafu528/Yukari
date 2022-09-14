package shibafu.yukari.database;

import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.ArrayList;
import java.util.List;

import shibafu.yukari.common.NotificationChannelPrefix;
import shibafu.yukari.linkage.ProviderStream;
import shibafu.yukari.mastodon.DefaultVisibilityCache;
import shibafu.yukari.linkage.ApiCollectionProvider;
import shibafu.yukari.linkage.StreamCollectionProvider;
import shibafu.yukari.mastodon.FetchDefaultVisibilityTask;

public class AccountManagerImpl implements AccountManager {
    private static final String LOG_TAG = "AccountManager";

    private final Context context;
    private final LocalBroadcastManager localBroadcastManager;
    private final CentralDatabase database;

    private final List<AuthUserRecord> users = new ArrayList<>();

    public AccountManagerImpl(Context context, CentralDatabase database) {
        this.context = context;
        this.localBroadcastManager = LocalBroadcastManager.getInstance(context);
        this.database = database;

        reloadUsers();
    }

    @Override
    public void reloadUsers() {
        List<AuthUserRecord> dbList;
        try (Cursor cursor = database.getAccounts()) {
            dbList = AuthUserRecord.getAccountsList(cursor);
        }

        //アカウント別通知チャンネルの設定をチェック
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        boolean enabledPerAccountChannel = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("pref_notif_per_account_channel", false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !enabledPerAccountChannel) {
            //アカウント別通知チャンネルを削除
            for (NotificationChannel channel : nm.getNotificationChannels()) {
                String groupId = channel.getGroup();
                if (groupId != null && groupId.startsWith(NotificationChannelPrefix.GROUP_ACCOUNT) && !groupId.replace(NotificationChannelPrefix.GROUP_ACCOUNT, "").equals("all")) {
                    nm.deleteNotificationChannel(channel.getId());
                }
            }
            for (NotificationChannelGroup group : nm.getNotificationChannelGroups()) {
                String groupId = group.getId();
                if (groupId.startsWith(NotificationChannelPrefix.GROUP_ACCOUNT) && !groupId.replace(NotificationChannelPrefix.GROUP_ACCOUNT, "").equals("all")) {
                    nm.deleteNotificationChannelGroup(groupId);
                }
            }
        }

        //消えたレコードの削除処理
        ArrayList<AuthUserRecord> removeList = new ArrayList<>();
        for (AuthUserRecord aur : users) {
            if (!dbList.contains(aur)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    String groupId = NotificationChannelPrefix.GROUP_ACCOUNT + aur.Url;

                    for (NotificationChannel channel : nm.getNotificationChannels()) {
                        if (groupId.equals(channel.getGroup())) {
                            nm.deleteNotificationChannel(channel.getId());
                        }
                    }

                    nm.deleteNotificationChannelGroup(groupId);
                }
                removeList.add(aur);
                Log.d(LOG_TAG, "Remove user: @" + aur.ScreenName);
            }
        }
        users.removeAll(removeList);

        //新しいレコードの登録
        ArrayList<AuthUserRecord> addedList = new ArrayList<>();
        for (AuthUserRecord aur : dbList) {
            if (!users.contains(aur)) {
                addedList.add(aur);
                users.add(aur);
                Log.d(LOG_TAG, "Add user: @" + aur.ScreenName);
            } else {
                AuthUserRecord existRecord = users.get(users.indexOf(aur));
                existRecord.update(aur);
                Log.d(LOG_TAG, "Update user: @" + aur.ScreenName);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && enabledPerAccountChannel) {
                AccountManager.createAccountNotificationChannels(nm, aur, false);
            }
        }
        Intent broadcastIntent = new Intent(AccountManager.ACTION_RELOADED_USERS);
        broadcastIntent.putExtra(AccountManager.EXTRA_RELOAD_REMOVED, removeList);
        broadcastIntent.putExtra(AccountManager.EXTRA_RELOAD_ADDED, addedList);
        localBroadcastManager.sendBroadcast(broadcastIntent);
        Log.d(LOG_TAG, "Reloaded users. User=" + users.size());
    }

    @Override
    @NonNull
    public List<AuthUserRecord> getUsers() {
        return users != null ? users : new ArrayList<>();
    }

    @Override
    @Nullable
    public AuthUserRecord getPrimaryUser() {
        // アカウントが1つも無いなら無理
        if (users.isEmpty()) {
            return null;
        }

        // プライマリアカウントを探して返す
        for (AuthUserRecord userRecord : users) {
            if (userRecord.isPrimary) {
                return userRecord;
            }
        }

        // プライマリアカウントが無いなら、とりあえず最初のレコードを返す
        return users.get(0);
    }

    @Override
    public void setPrimaryUser(long id) {
        for (AuthUserRecord userRecord : users) {
            userRecord.isPrimary = userRecord.InternalId == id;
        }
        storeUsers();
        reloadUsers();
    }

    @Override
    public ArrayList<AuthUserRecord> getWriterUsers() {
        ArrayList<AuthUserRecord> writers = new ArrayList<>();
        for (AuthUserRecord userRecord : users) {
            if (userRecord.isWriter) {
                writers.add(userRecord);
            }
        }
        return writers;
    }

    @Override
    public void setWriterUsers(List<AuthUserRecord> writers) {
        for (AuthUserRecord userRecord : users) {
            userRecord.isWriter = writers.contains(userRecord);
        }
        storeUsers();
    }

    @Override
    public void setUserColor(long id, int color) {
        for (AuthUserRecord user : users) {
            if (user.InternalId == id) {
                user.AccountColor = color;
                break;
            }
        }
        storeUsers();
    }

    @Override
    public void storeUsers() {
        database.beginTransaction();
        try {
            for (AuthUserRecord aur : users) {
                database.updateRecord(aur);
            }
            database.setTransactionSuccessful();
            Log.d(LOG_TAG, "Stored users to database.");
        } finally {
            database.endTransaction();
        }
    }

    @Override
    public void deleteUser(long id) {
        //Primaryの委譲が必要か確認する
        boolean delegatePrimary = false;
        for (AuthUserRecord aur : users) {
            if (aur.InternalId == id) {
                delegatePrimary = aur.isPrimary;
            }
        }
        if (users.size() > 0 && delegatePrimary) {
            users.get(0).isPrimary = true;
        }
        //削除以外のこれまでの変更を保存しておく
        storeUsers();
        //実際の削除を行う
        database.deleteRecord(AuthUserRecord.class, id);
        //データベースからアカウントをリロードする
        reloadUsers();
    }

    /**
     * 指定のAPI形式を扱える認証情報を検索します。プライマリフラグが設定されていれば、それを優先します。
     * @param apiType API形式。{@link Provider} 内の定数を参照。
     * @return 適合する認証情報。見つからない場合は null
     */
    @Override
    @Nullable
    public AuthUserRecord findPreferredUser(@ApiType int apiType) {
        AuthUserRecord found = null;

        for (AuthUserRecord user : users) {
            if (user.Provider.getApiType() == apiType) {
                if (user.isPrimary) {
                    return user;
                }

                if (found == null) {
                    found = user;
                }
            }
        }

        return found;
    }
}
