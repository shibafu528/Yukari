package shibafu.yukari.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.CursorWindow;
import android.database.sqlite.SQLiteCursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;
import shibafu.yukari.common.TabInfo;
import shibafu.yukari.common.TabType;
import shibafu.yukari.entity.StatusDraft;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Created by Shibafu on 13/12/18.
 */
@SuppressWarnings("TryWithIdenticalCatches")
public class CentralDatabase {

    //DB基本情報
    public static final String DB_FILENAME = "yukari.db";
    public static final int DB_VER = 17;

    //Accountsテーブル
    public static final String TABLE_ACCOUNTS = "Accounts";
    public static final String COL_ACCOUNTS_ID = "_id";
    @Deprecated private static final String COL_ACCOUNTS_CONSUMER_KEY = "ConsumerKey";
    @Deprecated private static final String COL_ACCOUNTS_CONSUMER_SECRET = "ConsumerSecret";
    public static final String COL_ACCOUNTS_ACCESS_TOKEN = "AccessToken";
    public static final String COL_ACCOUNTS_ACCESS_TOKEN_SECRET = "AccessTokenSecret";
    public static final String COL_ACCOUNTS_IS_PRIMARY = "IsPrimary"; //各種操作のメインアカウント、最初に認証した垢がデフォルト
    @Deprecated public static final String COL_ACCOUNTS_IS_ACTIVE  = "IsActive"; //タイムラインのアクティブアカウント
    public static final String COL_ACCOUNTS_IS_WRITER  = "IsWriter"; //ツイートのカレントアカウント、オープン毎にIsPrimaryで初期化する
    public static final String COL_ACCOUNTS_FALLBACK_TO= "FallbackTo"; //投稿規制フォールバック先ID、使わない場合は0
    public static final String COL_ACCOUNTS_COLOR = "AccountColor";
    public static final String COL_ACCOUNTS_PROVIDER_ID = "ProviderId";
    public static final String COL_ACCOUNTS_USER_ID = "UserId";
    public static final String COL_ACCOUNTS_SCREEN_NAME = "ScreenName";
    public static final String COL_ACCOUNTS_DISPLAY_NAME = "DisplayName";
    public static final String COL_ACCOUNTS_PROFILE_IMAGE_URL = "ProfileImageUrl";

    //Providersテーブル
    public static final String TABLE_PROVIDERS = "Providers";
    public static final String COL_PROVIDERS_ID = "_id";
    public static final String COL_PROVIDERS_HOST = "Host";
    public static final String COL_PROVIDERS_NAME = "Name";
    public static final String COL_PROVIDERS_API_TYPE = "ApiType";
    public static final String COL_PROVIDERS_CONSUMER_KEY = "ConsumerKey";
    public static final String COL_PROVIDERS_CONSUMER_SECRET = "ConsumerSecret";

    //Userテーブル
    public static final String TABLE_USER = "User";
    public static final String COL_USER_ID = "_id";
    public static final String COL_USER_SCREEN_NAME = "ScreenName";
    public static final String COL_USER_NAME = "Name";
    public static final String COL_USER_DESCRIPTION = "Description";
    public static final String COL_USER_DESCRIPTION_URLENTITIES = "DescriptionURLEntities";
    public static final String COL_USER_LOCATION = "Location";
    public static final String COL_USER_URL = "Url";
    public static final String COL_USER_PROFILE_IMAGE_URL = "ProfileImageUrl";
    public static final String COL_USER_PROFILE_BANNER_URL = "ProfileBannerUrl";
    public static final String COL_USER_IS_PROTECTED = "IsProtected";
    public static final String COL_USER_IS_VERIFIED = "IsVerified";
    public static final String COL_USER_IS_TRANSLATOR = "IsTranslator";
    public static final String COL_USER_IS_CONTRIBUTORS_ENABLED = "IsContributorsEnabled";
    public static final String COL_USER_IS_GEO_ENABLED = "IsGeoEnabled";
    public static final String COL_USER_STATUSES_COUNT = "StatusesCount";
    public static final String COL_USER_FOLLOWINGS_COUNT = "FollowingsCount";
    public static final String COL_USER_FOLLOWERS_COUNT = "FollowersCount";
    public static final String COL_USER_FAVORITES_COUNT = "FavoritesCount";
    public static final String COL_USER_LISTED_COUNT = "ListedCount";
    public static final String COL_USER_LANGUAGE = "Language";
    public static final String COL_USER_CREATED_AT = "CreatedAt";
    public static final String COL_USER_NAME_ALT = "UserName";

    //UserExtrasテーブル
    public static final String TABLE_USER_EXTRAS = "UserExtras";
    public static final String COL_UEXTRAS_ID = "_id";
    public static final String COL_UEXTRAS_COLOR = "UserColor";
    public static final String COL_UEXTRAS_PRIORITY_ID = "PriorityAccountId";

    //Draftsテーブル
    public static final String TABLE_DRAFTS = "Drafts";
    public static final String COL_DRAFTS_ID = "_id";
    public static final String COL_DRAFTS_WRITER_ID = "WriterId";
    public static final String COL_DRAFTS_TEXT = "Text";
    public static final String COL_DRAFTS_DATETIME = "DateTime";
    public static final String COL_DRAFTS_IN_REPLY_TO = "InReplyTo";//IsDirectMessage時は送信先ユーザIDを格納するカラムとする
    public static final String COL_DRAFTS_IS_QUOTED = "IsQuoted";
    public static final String COL_DRAFTS_ATTACHED_PICTURE = "AttachedPicture";
    public static final String COL_DRAFTS_USE_GEO_LOCATION = "UseGeoLocation";
    public static final String COL_DRAFTS_GEO_LATITUDE = "GeoLatitude";
    public static final String COL_DRAFTS_GEO_LONGITUDE = "GeoLongitude";
    public static final String COL_DRAFTS_IS_POSSIBLY_SENSITIVE = "IsPossiblySensitive";
    public static final String COL_DRAFTS_IS_DIRECT_MESSAGE = "IsDirectMessage";
    public static final String COL_DRAFTS_IS_FAILED_DELIVERY= "IsFailedDelivery";//送信失敗が原因で保存されたツイート
    public static final String COL_DRAFTS_MESSAGE_TARGET = "MessageTarget";
    public static final String COL_DRAFTS_VISIBILITY = "Visibility";
    public static final String COL_DRAFTS_SPOILER_TEXT = "SpoilerText";

    //Bookmarksテーブル
    public static final String TABLE_BOOKMARKS = "Bookmarks";
    public static final String COL_BOOKMARKS_ID = "_id";
    public static final String COL_BOOKMARKS_RECEIVER_ID = "ReceiverId";
    public static final String COL_BOOKMARKS_SAVE_DATE = "SaveDate";
    public static final String COL_BOOKMARKS_BLOB = "Blob";

    //Tabsテーブル
    public static final String TABLE_TABS = "Tabs";
    public static final String COL_TABS_ID = "_id";
    public static final String COL_TABS_TYPE = "Type";
    public static final String COL_TABS_TAB_ORDER = "TabOrder";
    public static final String COL_TABS_BIND_ACCOUNT_ID = "BindAccountId";
    public static final String COL_TABS_BIND_LIST_ID = "BindListId";
    public static final String COL_TABS_SEARCH_KEYWORD = "SearchKeyword";
    public static final String COL_TABS_FILTER_QUERY = "FilterQuery";
    public static final String COL_TABS_IS_STARTUP = "IsStartup";

    //SearchHistoryテーブル
    public static final String TABLE_SEARCH_HISTORY = "SearchHistory";
    public static final String COL_SHISTORY_ID = "_id";
    public static final String COL_SHISTORY_QUERY = "query";
    public static final String COL_SHISTORY_DATE = "date";

    //Muteテーブル
    public static final String TABLE_MUTE = "Mute";
    public static final String COL_MUTE_ID = "_id";
    public static final String COL_MUTE_SCOPE = "Scope";
    public static final String COL_MUTE_MATCH = "Match";
    public static final String COL_MUTE_MUTE = "Mute";
    public static final String COL_MUTE_QUERY = "Query";
    public static final String COL_MUTE_EXPIRATION_TIME_MILLIS = "ExpirationTimeMillis"; //"ExpirationDate";

    //AutoMuteテーブル
    public static final String TABLE_AUTO_MUTE = "AutoMute";
    public static final String COL_AUTO_MUTE_ID = "_id";
    public static final String COL_AUTO_MUTE_MATCH = "Match";
    public static final String COL_AUTO_MUTE_QUERY = "Query";

    //Templateテーブル
    @Deprecated private static final String TABLE_TEMPLATE = "Template";
    @Deprecated private static final String COL_TEMPLATE_ID = "_id";
    @Deprecated private static final String COL_TEMPLATE_VALUE = "Value";

    //StreamChannelStatesテーブル
    public static final String TABLE_STREAM_CHANNEL_STATES = "StreamChannelStates";
    public static final String COL_STREAM_CHANNEL_STATES_ID = "_id";
    public static final String COL_STREAM_CHANNEL_STATES_ACCOUNT_ID = "AccountId";
    public static final String COL_STREAM_CHANNEL_STATES_CHANNEL_ID = "ChannelId";
    public static final String COL_STREAM_CHANNEL_STATES_IS_ACTIVE = "IsActive";

    private CentralDBHelper helper;
    private SQLiteDatabase db;


    private static class CentralDBHelper extends SQLiteOpenHelper {

        public CentralDBHelper(Context context) {
            super(context, DB_FILENAME, null, DB_VER);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(
                    "CREATE TABLE " + TABLE_ACCOUNTS + " (" +
                    COL_ACCOUNTS_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COL_ACCOUNTS_USER_ID + " INTEGER, " +
                    COL_ACCOUNTS_SCREEN_NAME + " TEXT, " +
                    COL_ACCOUNTS_DISPLAY_NAME + " TEXT, " +
                    COL_ACCOUNTS_PROFILE_IMAGE_URL + " TEXT, " +
                    COL_ACCOUNTS_ACCESS_TOKEN + " TEXT, " +
                    COL_ACCOUNTS_ACCESS_TOKEN_SECRET + " TEXT, " +
                    COL_ACCOUNTS_IS_PRIMARY + " INTEGER, " +
                    COL_ACCOUNTS_IS_ACTIVE + " INTEGER, " +
                    COL_ACCOUNTS_IS_WRITER + " INTEGER, " +
                    COL_ACCOUNTS_FALLBACK_TO + " INTEGER, " +
                    COL_ACCOUNTS_COLOR + " INTEGER, " +
                    COL_ACCOUNTS_PROVIDER_ID + " INTEGER)"
            );
            db.execSQL(
                    "CREATE TABLE " + TABLE_PROVIDERS + " (" +
                            COL_PROVIDERS_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            COL_PROVIDERS_HOST + " TEXT, " +
                            COL_PROVIDERS_NAME + " TEXT, " +
                            COL_PROVIDERS_API_TYPE + " INTEGER, " +
                            COL_PROVIDERS_CONSUMER_KEY + " TEXT, " +
                            COL_PROVIDERS_CONSUMER_SECRET + " TEXT)"
            );
            db.execSQL(
                    "CREATE TABLE " + TABLE_USER + " (" +
                    COL_USER_ID + " INTEGER PRIMARY KEY, " +
                    COL_USER_SCREEN_NAME + " TEXT, " +
                    COL_USER_NAME + " TEXT, " +
                    COL_USER_DESCRIPTION + " TEXT, " +
                    COL_USER_DESCRIPTION_URLENTITIES + " BLOB," +
                    COL_USER_LOCATION + " TEXT, " +
                    COL_USER_URL + " TEXT, " +
                    COL_USER_PROFILE_IMAGE_URL + " TEXT, " +
                    COL_USER_PROFILE_BANNER_URL + " TEXT, " +
                    COL_USER_IS_PROTECTED + " INTEGER, " +
                    COL_USER_IS_VERIFIED + " INTEGER, " +
                    COL_USER_IS_TRANSLATOR + " INTEGER, " +
                    COL_USER_IS_CONTRIBUTORS_ENABLED + " INTEGER, " +
                    COL_USER_IS_GEO_ENABLED + " INTEGER, " +
                    COL_USER_STATUSES_COUNT + " INTEGER, " +
                    COL_USER_FOLLOWINGS_COUNT + " INTEGER, " +
                    COL_USER_FOLLOWERS_COUNT + " INTEGER, " +
                    COL_USER_FAVORITES_COUNT + " INTEGER, " +
                    COL_USER_LISTED_COUNT + " INTEGER, " +
                    COL_USER_LANGUAGE + " TEXT, " +
                    COL_USER_CREATED_AT + " INTEGER)"
            );
            db.execSQL(
                    "CREATE TABLE " + TABLE_USER_EXTRAS + " (" +
                    COL_UEXTRAS_ID + " TEXT PRIMARY KEY, " +
                    COL_UEXTRAS_COLOR + " INTEGER, " +
                    COL_UEXTRAS_PRIORITY_ID + " INTEGER)"
            );
            db.execSQL(
                    "CREATE TABLE " + TABLE_DRAFTS + " (" +
                    COL_DRAFTS_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COL_DRAFTS_WRITER_ID + " INTEGER, " +
                    COL_DRAFTS_TEXT + " TEXT, " +
                    COL_DRAFTS_DATETIME + " TEXT, " +
                    COL_DRAFTS_IN_REPLY_TO + " TEXT, " +
                    COL_DRAFTS_IS_QUOTED + " INTEGER, " +
                    COL_DRAFTS_ATTACHED_PICTURE + " TEXT, " +
                    COL_DRAFTS_USE_GEO_LOCATION + " INTEGER, " +
                    COL_DRAFTS_GEO_LATITUDE + " REAL, " +
                    COL_DRAFTS_GEO_LONGITUDE + " REAL, " +
                    COL_DRAFTS_IS_POSSIBLY_SENSITIVE + " INTEGER, " +
                    COL_DRAFTS_IS_DIRECT_MESSAGE + " INTEGER, " +
                    COL_DRAFTS_IS_FAILED_DELIVERY + " INTEGER, " +
                    COL_DRAFTS_MESSAGE_TARGET + " TEXT DEFAULT '', " +
                    COL_DRAFTS_VISIBILITY + " INTEGER DEFAULT 0, " +
                    COL_DRAFTS_SPOILER_TEXT + " TEXT)"
            );
            db.execSQL(
                    "CREATE TABLE " + TABLE_TABS + " (" +
                    COL_TABS_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COL_TABS_TYPE + " INTEGER, " +
                    COL_TABS_TAB_ORDER + " INTEGER, " +
                    COL_TABS_BIND_ACCOUNT_ID + " INTEGER, " +
                    COL_TABS_BIND_LIST_ID + " INTEGER, " +
                    COL_TABS_SEARCH_KEYWORD + " TEXT, " +
                    COL_TABS_FILTER_QUERY + " TEXT, " +
                    COL_TABS_IS_STARTUP + " INTEGER)"
            );
            {
                TabInfo homeTab = new TabInfo(TabType.TABTYPE_HOME, 0, null);
                homeTab.setStartup(true);
                db.insert(TABLE_TABS, null, homeTab.getContentValues());
                TabInfo mentionTab = new TabInfo(TabType.TABTYPE_MENTION, 1, null);
                db.insert(TABLE_TABS, null, mentionTab.getContentValues());
                TabInfo dmTab = new TabInfo(TabType.TABTYPE_DM, 2, null);
                db.insert(TABLE_TABS, null, dmTab.getContentValues());
                TabInfo historyTab = new TabInfo(TabType.TABTYPE_HISTORY, 2, null);
                db.insert(TABLE_TABS, null, historyTab.getContentValues());
            }
            db.execSQL(
                    "CREATE TABLE " + TABLE_SEARCH_HISTORY + " (" +
                    COL_SHISTORY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    COL_SHISTORY_QUERY + " TEXT UNIQUE, " +
                    COL_SHISTORY_DATE + " INTEGER)"
            );
            db.execSQL(
                    "CREATE TABLE " + TABLE_MUTE + " (" +
                            COL_MUTE_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            COL_MUTE_SCOPE + " INTEGER, " +
                            COL_MUTE_MATCH + " INTEGER, " +
                            COL_MUTE_MUTE + " INTEGER, " +
                            COL_MUTE_QUERY + " TEXT, " +
                            COL_MUTE_EXPIRATION_TIME_MILLIS + " INTEGER DEFAULT -1)"
            );
            db.execSQL(
                    "CREATE TABLE " + TABLE_BOOKMARKS + " (" +
                            COL_BOOKMARKS_ID + " INTEGER PRIMARY KEY, " +
                            COL_BOOKMARKS_RECEIVER_ID + " INTEGER, " +
                            COL_BOOKMARKS_SAVE_DATE + " INTEGER, " +
                            COL_BOOKMARKS_BLOB + " BLOB NOT NULL)"
            );
            db.execSQL(
                    "CREATE TABLE " + TABLE_AUTO_MUTE + " (" +
                            COL_AUTO_MUTE_ID + " INTEGER PRIMARY KEY, " +
                            COL_AUTO_MUTE_MATCH + " INTEGER, " +
                            COL_AUTO_MUTE_QUERY + " TEXT)"
            );
            db.execSQL(
                    "CREATE TABLE " + TABLE_STREAM_CHANNEL_STATES + " (" +
                            COL_STREAM_CHANNEL_STATES_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                            COL_STREAM_CHANNEL_STATES_ACCOUNT_ID + " TEXT NOT NULL, " +
                            COL_STREAM_CHANNEL_STATES_CHANNEL_ID + " TEXT NOT NULL, " +
                            COL_STREAM_CHANNEL_STATES_IS_ACTIVE + " INTEGER NOT NULL)"
            );
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if (oldVersion == 1) {
                TabInfo homeTab = new TabInfo(TabType.TABTYPE_HOME, 0, null);
                homeTab.setStartup(true);
                db.insert(TABLE_TABS, null, homeTab.getContentValues());
                TabInfo mentionTab = new TabInfo(TabType.TABTYPE_MENTION, 1, null);
                db.insert(TABLE_TABS, null, mentionTab.getContentValues());
                TabInfo dmTab = new TabInfo(TabType.TABTYPE_DM, 2, null);
                db.insert(TABLE_TABS, null, dmTab.getContentValues());
                ++oldVersion;
            }
            if (oldVersion == 2) {
                db.execSQL(
                        "CREATE TABLE " + TABLE_SEARCH_HISTORY + " (" +
                        COL_SHISTORY_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        COL_SHISTORY_QUERY + " TEXT UNIQUE, " +
                        COL_SHISTORY_DATE + " INTEGER)"
                );
                ++oldVersion;
            }
            if (oldVersion == 3) {
                db.execSQL(
                        "ALTER TABLE " + TABLE_DRAFTS + " ADD " + COL_DRAFTS_MESSAGE_TARGET +
                        " TEXT DEFAULT ''"
                );
                ++oldVersion;
            }
            if (oldVersion == 4) {
                db.execSQL(
                        "CREATE TABLE " + TABLE_MUTE + " (" +
                                COL_MUTE_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                COL_MUTE_SCOPE + " INTEGER, " +
                                COL_MUTE_MATCH + " INTEGER, " +
                                COL_MUTE_MUTE + " INTEGER, " +
                                COL_MUTE_QUERY + " TEXT)"
                );
                ++oldVersion;
            }
            if (oldVersion == 5) {
                db.execSQL("ALTER TABLE " + TABLE_MUTE + " ADD ExpirationDate INTEGER DEFAULT -1");
                ++oldVersion;
            }
            if (oldVersion == 6) {
                db.execSQL("ALTER TABLE " + TABLE_MUTE + " RENAME TO tmp_Mute");
                db.execSQL(
                        "CREATE TABLE " + TABLE_MUTE + " (" +
                                COL_MUTE_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                COL_MUTE_SCOPE + " INTEGER, " +
                                COL_MUTE_MATCH + " INTEGER, " +
                                COL_MUTE_MUTE + " INTEGER, " +
                                COL_MUTE_QUERY + " TEXT, " +
                                COL_MUTE_EXPIRATION_TIME_MILLIS + " INTEGER DEFAULT -1)"
                );
                db.execSQL("INSERT INTO " + TABLE_MUTE + "(" +
                        COL_MUTE_ID + ", " +
                        COL_MUTE_SCOPE + ", " +
                        COL_MUTE_MATCH + ", " +
                        COL_MUTE_MUTE + ", " +
                        COL_MUTE_QUERY + ", " +
                        COL_MUTE_EXPIRATION_TIME_MILLIS + ") SELECT " +
                        COL_MUTE_ID + ", " +
                        COL_MUTE_SCOPE + ", " +
                        COL_MUTE_MATCH + ", " +
                        COL_MUTE_MUTE + ", " +
                        COL_MUTE_QUERY + ", " +
                        "ExpirationDate FROM tmp_Mute");
                db.execSQL("DROP TABLE tmp_Mute");
                ++oldVersion;
            }
            if (oldVersion == 7) {
                db.execSQL("ALTER TABLE " + TABLE_ACCOUNTS + " ADD " + COL_ACCOUNTS_COLOR + " INTEGER DEFAULT 0");
                ++oldVersion;
            }
            if (oldVersion == 8) {
                db.execSQL(
                        "CREATE TABLE " + TABLE_USER_EXTRAS + " (" +
                        COL_UEXTRAS_ID + " INTEGER PRIMARY KEY, " +
                        COL_UEXTRAS_COLOR + " INTEGER, " +
                        COL_UEXTRAS_PRIORITY_ID + " INTEGER)"
                );
                ++oldVersion;
            }
            if (oldVersion == 9) {
                db.execSQL(
                        "CREATE TABLE " + TABLE_BOOKMARKS + " (" +
                                COL_BOOKMARKS_ID + " INTEGER PRIMARY KEY, " +
                                COL_BOOKMARKS_RECEIVER_ID + " INTEGER, " +
                                COL_BOOKMARKS_SAVE_DATE + " INTEGER, " +
                                COL_BOOKMARKS_BLOB + " BLOB NOT NULL)"
                );
                ++oldVersion;
            }
            if (oldVersion == 10) {
                db.execSQL(
                        "CREATE TABLE " + TABLE_AUTO_MUTE + " (" +
                                COL_AUTO_MUTE_ID + " INTEGER PRIMARY KEY, " +
                                COL_AUTO_MUTE_MATCH + " INTEGER, " +
                                COL_AUTO_MUTE_QUERY + " TEXT)"
                );
                ++oldVersion;
            }
            if (oldVersion == 11) {
                db.execSQL("ALTER TABLE " + TABLE_TABS + " ADD " + COL_TABS_IS_STARTUP + " INTEGER DEFAULT 0");
                ++oldVersion;
            }
            if (oldVersion == 12) {
                //noinspection deprecation
                db.execSQL("ALTER TABLE " + TABLE_ACCOUNTS + " ADD " + COL_ACCOUNTS_CONSUMER_KEY + " TEXT");
                //noinspection deprecation
                db.execSQL("ALTER TABLE " + TABLE_ACCOUNTS + " ADD " + COL_ACCOUNTS_CONSUMER_SECRET + " TEXT");
                ++oldVersion;
            }
            if (oldVersion == 13) {
                //noinspection deprecation
                db.execSQL(
                        "CREATE TABLE " + TABLE_TEMPLATE + " (" +
                                COL_TEMPLATE_ID + " INTEGER PRIMARY KEY, " +
                                COL_TEMPLATE_VALUE + " TEXT)"
                );
                ++oldVersion;
            }
            if (oldVersion == 14) {
                //noinspection deprecation
                db.execSQL("DROP TABLE " + TABLE_TEMPLATE);
                ++oldVersion;
            }
            if (oldVersion == 15) {
                db.execSQL("ALTER TABLE " + TABLE_ACCOUNTS + " ADD " + COL_ACCOUNTS_PROVIDER_ID + " INTEGER");
                db.execSQL(
                        "CREATE TABLE " + TABLE_PROVIDERS + " (" +
                                COL_PROVIDERS_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                COL_PROVIDERS_HOST + " TEXT, " +
                                COL_PROVIDERS_NAME + " TEXT, " +
                                COL_PROVIDERS_API_TYPE + " INTEGER, " +
                                COL_PROVIDERS_CONSUMER_KEY + " TEXT, " +
                                COL_PROVIDERS_CONSUMER_SECRET + " TEXT)"
                );
                ++oldVersion;
            }
            if (oldVersion == 16) {
                db.execSQL("ALTER TABLE " + TABLE_ACCOUNTS + " RENAME TO tmp_Accounts");
                db.execSQL(
                        "CREATE TABLE " + TABLE_ACCOUNTS + " (" +
                                COL_ACCOUNTS_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                COL_ACCOUNTS_USER_ID + " INTEGER, " +
                                COL_ACCOUNTS_SCREEN_NAME + " TEXT, " +
                                COL_ACCOUNTS_DISPLAY_NAME + " TEXT, " +
                                COL_ACCOUNTS_PROFILE_IMAGE_URL + " TEXT, " +
                                COL_ACCOUNTS_ACCESS_TOKEN + " TEXT, " +
                                COL_ACCOUNTS_ACCESS_TOKEN_SECRET + " TEXT, " +
                                COL_ACCOUNTS_IS_PRIMARY + " INTEGER, " +
                                COL_ACCOUNTS_IS_ACTIVE + " INTEGER, " +
                                COL_ACCOUNTS_IS_WRITER + " INTEGER, " +
                                COL_ACCOUNTS_FALLBACK_TO + " INTEGER, " +
                                COL_ACCOUNTS_COLOR + " INTEGER, " +
                                COL_ACCOUNTS_PROVIDER_ID + " INTEGER)"
                );
                db.execSQL("INSERT INTO " + TABLE_ACCOUNTS + " (" +
                        COL_ACCOUNTS_USER_ID + "," +
                        COL_ACCOUNTS_SCREEN_NAME + "," +
                        COL_ACCOUNTS_DISPLAY_NAME + "," +
                        COL_ACCOUNTS_PROFILE_IMAGE_URL + "," +
                        COL_ACCOUNTS_ACCESS_TOKEN + "," +
                        COL_ACCOUNTS_ACCESS_TOKEN_SECRET + "," +
                        COL_ACCOUNTS_IS_PRIMARY + "," +
                        COL_ACCOUNTS_IS_ACTIVE + "," +
                        COL_ACCOUNTS_IS_WRITER + "," +
                        COL_ACCOUNTS_FALLBACK_TO + "," +
                        COL_ACCOUNTS_COLOR + "," +
                        COL_ACCOUNTS_PROVIDER_ID +
                        ") SELECT " +
                        TABLE_USER + "." + COL_USER_ID + "," +
                        TABLE_USER + "." + COL_USER_SCREEN_NAME + "," +
                        TABLE_USER + "." + COL_USER_NAME + "," +
                        TABLE_USER + "." + COL_USER_PROFILE_IMAGE_URL + "," +
                        " tmp_Accounts." + COL_ACCOUNTS_ACCESS_TOKEN + "," +
                        " tmp_Accounts." + COL_ACCOUNTS_ACCESS_TOKEN_SECRET + "," +
                        " tmp_Accounts." + COL_ACCOUNTS_IS_PRIMARY + "," +
                        " tmp_Accounts." + COL_ACCOUNTS_IS_ACTIVE + "," +
                        " tmp_Accounts." + COL_ACCOUNTS_IS_WRITER + "," +
                        " tmp_Accounts." + COL_ACCOUNTS_FALLBACK_TO + "," +
                        " tmp_Accounts." + COL_ACCOUNTS_COLOR + "," +
                        " tmp_Accounts." + COL_ACCOUNTS_PROVIDER_ID +
                        " FROM tmp_Accounts " +
                        " INNER JOIN " + TABLE_USER +
                        " ON " +
                        " tmp_Accounts." + COL_ACCOUNTS_ID + " = " + TABLE_USER + "." + COL_USER_ID);
                db.execSQL("DROP TABLE tmp_Accounts");

                // アカウントIDを持っているテーブルの更新準備
                StringBuilder whenBuilder = new StringBuilder();
                final Cursor c = db.query(TABLE_ACCOUNTS, new String[]{COL_ACCOUNTS_ID, COL_ACCOUNTS_USER_ID}, COL_ACCOUNTS_PROVIDER_ID + " IS NULL", null, null, null, null);
                try {
                    if (c.moveToFirst()) {
                        do {
                            long twitterUserId = c.getLong(c.getColumnIndex(COL_ACCOUNTS_USER_ID));
                            long accountId = c.getLong(c.getColumnIndex(COL_ACCOUNTS_ID));

                            whenBuilder.append(" WHEN ").append(twitterUserId).append(" THEN ").append(accountId);
                        } while (c.moveToNext());
                    }
                } finally {
                    c.close();
                }
                whenBuilder.append(" ELSE NULL ");
                String when = whenBuilder.toString();

                db.execSQL("UPDATE " + TABLE_DRAFTS +
                        " SET " + COL_DRAFTS_WRITER_ID + " = CASE " + COL_DRAFTS_WRITER_ID + when + " END " +
                        " WHERE " + COL_DRAFTS_WRITER_ID + " IS NOT NULL");

                db.execSQL("UPDATE " + TABLE_BOOKMARKS +
                        " SET " + COL_BOOKMARKS_RECEIVER_ID + " = CASE " + COL_BOOKMARKS_RECEIVER_ID + when + " END " +
                        " WHERE " + COL_BOOKMARKS_RECEIVER_ID + " IS NOT NULL");

                db.execSQL("UPDATE " + TABLE_TABS +
                        " SET " + COL_TABS_BIND_ACCOUNT_ID + " = CASE " + COL_TABS_BIND_ACCOUNT_ID + when + " END " +
                        " WHERE " + COL_TABS_BIND_ACCOUNT_ID + " IS NOT NULL");

                db.execSQL(
                        "CREATE TABLE " + TABLE_STREAM_CHANNEL_STATES + " (" +
                                COL_STREAM_CHANNEL_STATES_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                COL_STREAM_CHANNEL_STATES_ACCOUNT_ID + " TEXT NOT NULL, " +
                                COL_STREAM_CHANNEL_STATES_CHANNEL_ID + " TEXT NOT NULL, " +
                                COL_STREAM_CHANNEL_STATES_IS_ACTIVE + " INTEGER NOT NULL)"
                );
                db.execSQL(
                        "INSERT INTO " + TABLE_STREAM_CHANNEL_STATES + " (" +
                                COL_STREAM_CHANNEL_STATES_ACCOUNT_ID + "," +
                                COL_STREAM_CHANNEL_STATES_CHANNEL_ID + "," +
                                COL_STREAM_CHANNEL_STATES_IS_ACTIVE +
                                ") SELECT " +
                                TABLE_ACCOUNTS + "." + COL_ACCOUNTS_ID + "," +
                                "?," +
                                "1 " +
                                " FROM " +
                                TABLE_ACCOUNTS +
                                " WHERE " +
                                TABLE_ACCOUNTS + "." + COL_ACCOUNTS_IS_ACTIVE + " = 1",
                        new Object[]{"UserStream"}
                );

                db.execSQL("ALTER TABLE " + TABLE_DRAFTS + " RENAME TO tmp_Drafts");
                db.execSQL(
                        "CREATE TABLE " + TABLE_DRAFTS + " (" +
                                COL_DRAFTS_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                COL_DRAFTS_WRITER_ID + " INTEGER, " +
                                COL_DRAFTS_TEXT + " TEXT, " +
                                COL_DRAFTS_DATETIME + " TEXT, " +
                                COL_DRAFTS_IN_REPLY_TO + " TEXT, " +
                                COL_DRAFTS_IS_QUOTED + " INTEGER, " +
                                COL_DRAFTS_ATTACHED_PICTURE + " TEXT, " +
                                COL_DRAFTS_USE_GEO_LOCATION + " INTEGER, " +
                                COL_DRAFTS_GEO_LATITUDE + " REAL, " +
                                COL_DRAFTS_GEO_LONGITUDE + " REAL, " +
                                COL_DRAFTS_IS_POSSIBLY_SENSITIVE + " INTEGER, " +
                                COL_DRAFTS_IS_DIRECT_MESSAGE + " INTEGER, " +
                                COL_DRAFTS_IS_FAILED_DELIVERY + " INTEGER, " +
                                COL_DRAFTS_MESSAGE_TARGET + " TEXT DEFAULT '', " +
                                COL_DRAFTS_VISIBILITY + " INTEGER DEFAULT 0, " +
                                COL_DRAFTS_SPOILER_TEXT + " TEXT)"
                );
                db.execSQL("INSERT INTO " + TABLE_DRAFTS + "(" +
                        COL_DRAFTS_WRITER_ID + ", " +
                        COL_DRAFTS_TEXT + ", " +
                        COL_DRAFTS_DATETIME + ", " +
                        COL_DRAFTS_IN_REPLY_TO + ", " +
                        COL_DRAFTS_IS_QUOTED + ", " +
                        COL_DRAFTS_ATTACHED_PICTURE + ", " +
                        COL_DRAFTS_USE_GEO_LOCATION + ", " +
                        COL_DRAFTS_GEO_LATITUDE + ", " +
                        COL_DRAFTS_GEO_LONGITUDE + ", " +
                        COL_DRAFTS_IS_POSSIBLY_SENSITIVE + ", " +
                        COL_DRAFTS_IS_DIRECT_MESSAGE + ", " +
                        COL_DRAFTS_IS_FAILED_DELIVERY + ", " +
                        COL_DRAFTS_MESSAGE_TARGET + ", " +
                        COL_DRAFTS_VISIBILITY + ", " +
                        COL_DRAFTS_SPOILER_TEXT +
                        ") SELECT " +
                        COL_DRAFTS_WRITER_ID + ", " +
                        COL_DRAFTS_TEXT + ", " +
                        COL_DRAFTS_DATETIME + ", " +
                        "CASE WHEN " + COL_DRAFTS_IN_REPLY_TO + " IS NULL OR " + COL_DRAFTS_IN_REPLY_TO + " <= 0 THEN NULL " +
                        "     ELSE CASE WHEN " + COL_DRAFTS_IS_DIRECT_MESSAGE + " = 1 THEN 'https://twitter.com/intent/user?user_id='||" + COL_DRAFTS_IN_REPLY_TO +
                        "               ELSE 'https://twitter.com/null/status/'||" + COL_DRAFTS_IN_REPLY_TO +
                        "          END " +
                        " END, " +
                        COL_DRAFTS_IS_QUOTED + ", " +
                        COL_DRAFTS_ATTACHED_PICTURE + ", " +
                        COL_DRAFTS_USE_GEO_LOCATION + ", " +
                        COL_DRAFTS_GEO_LATITUDE + ", " +
                        COL_DRAFTS_GEO_LONGITUDE + ", " +
                        COL_DRAFTS_IS_POSSIBLY_SENSITIVE + ", " +
                        COL_DRAFTS_IS_DIRECT_MESSAGE + ", " +
                        COL_DRAFTS_IS_FAILED_DELIVERY + ", " +
                        COL_DRAFTS_MESSAGE_TARGET + ", " +
                        "0, " +
                        "NULL " +
                        " FROM tmp_Drafts"
                );
                db.execSQL("DROP TABLE tmp_Drafts");

                db.execSQL("ALTER TABLE " + TABLE_USER_EXTRAS + " RENAME TO tmp_UserExtras");
                db.execSQL(
                        "CREATE TABLE " + TABLE_USER_EXTRAS + " (" +
                                COL_UEXTRAS_ID + " TEXT PRIMARY KEY, " +
                                COL_UEXTRAS_COLOR + " INTEGER, " +
                                COL_UEXTRAS_PRIORITY_ID + " INTEGER)"
                );
                db.execSQL("INSERT INTO " + TABLE_USER_EXTRAS + "(" +
                        COL_UEXTRAS_ID + ", " +
                        COL_UEXTRAS_COLOR + ", " +
                        COL_UEXTRAS_PRIORITY_ID +
                        ") SELECT " +
                        "'https://twitter.com/intent/user?user_id='||" + COL_UEXTRAS_ID + ", " +
                        COL_UEXTRAS_COLOR + ", " +
                        "CASE " + COL_UEXTRAS_PRIORITY_ID + when + " END " +
                        " FROM tmp_UserExtras"
                );
                db.execSQL("DROP TABLE tmp_UserExtras");

                ++oldVersion;
            }
        }
    }

    public CentralDatabase(Context context) {
        helper = new CentralDBHelper(context);
    }

    public CentralDatabase open() {
        db = helper.getWritableDatabase();
        //Accounts.IsWriterの初期化を行う
        beginTransaction();
        try {
            db.execSQL("UPDATE " + TABLE_ACCOUNTS + " SET " + COL_ACCOUNTS_IS_WRITER + " = 0");
            db.execSQL("UPDATE " + TABLE_ACCOUNTS + " SET " + COL_ACCOUNTS_IS_WRITER + " = 1 WHERE " + COL_ACCOUNTS_IS_PRIMARY + " = 1");
            setTransactionSuccessful();
        } finally {
            endTransaction();
        }
        return this;
    }

    public void close() {
        db.close();
    }

    public void beginTransaction() {
        db.beginTransaction();
    }

    public void setTransactionSuccessful() {
        db.setTransactionSuccessful();
    }

    public void endTransaction() {
        db.endTransaction();
    }

    public void vacuum() {
        db.execSQL("vacuum");
    }

    //<editor-fold desc="Users">
    public DBUser getUser(long id) {
        Cursor c = db.query(TABLE_USER, null, COL_USER_ID + "=" + id, null, null, null, null);
        DBUser user = null;
        try {
            if (c.moveToFirst()) {
                user = new DBUser(c);
            }
        } finally {
            c.close();
        }
        return user;
    }

    public DBUser getUser(String screenName) {
        Cursor c = db.query(TABLE_USER, null, COL_USER_SCREEN_NAME + "=?", new String[]{screenName}, null, null, null);
        DBUser user = null;
        try {
            if (c.moveToFirst()) {
                user = new DBUser(c);
            }
        } finally {
            c.close();
        }
        return user;
    }

    public Cursor getUsersCursor(String where, String[] whereClause) {
        return db.query(TABLE_USER, null, where, whereClause, null, null, COL_USER_SCREEN_NAME + " ASC");
    }

    public Cursor getUsersCursor() {
        return db.query(TABLE_USER, null, null, null, null, null, COL_USER_SCREEN_NAME + " ASC");
    }

    public void wipeUsers() {
        beginTransaction();
        try {
            db.delete(TABLE_USER, COL_USER_ID + " NOT IN (SELECT " + COL_ACCOUNTS_ID + " FROM " + TABLE_ACCOUNTS + ")", null);
            setTransactionSuccessful();
        } finally {
            endTransaction();
        }
    }
    //</editor-fold>

    //<editor-fold desc="Accounts">
    public Cursor getAccounts() {
        return db.query(
                TABLE_ACCOUNTS
                        + " LEFT JOIN " + TABLE_PROVIDERS + " ON " + TABLE_ACCOUNTS + "." + COL_ACCOUNTS_PROVIDER_ID + "=" + TABLE_PROVIDERS + "." + COL_PROVIDERS_ID,
                new String[]{
                        TABLE_ACCOUNTS + "." + COL_ACCOUNTS_ID,
                        TABLE_ACCOUNTS + "." + COL_ACCOUNTS_USER_ID,
                        TABLE_ACCOUNTS + "." + COL_ACCOUNTS_SCREEN_NAME,
                        TABLE_ACCOUNTS + "." + COL_ACCOUNTS_DISPLAY_NAME,
                        TABLE_ACCOUNTS + "." + COL_ACCOUNTS_PROFILE_IMAGE_URL,
                        TABLE_ACCOUNTS + "." + COL_ACCOUNTS_ACCESS_TOKEN,
                        TABLE_ACCOUNTS + "." + COL_ACCOUNTS_ACCESS_TOKEN_SECRET,
                        TABLE_ACCOUNTS + "." + COL_ACCOUNTS_IS_PRIMARY,
                        TABLE_ACCOUNTS + "." + COL_ACCOUNTS_IS_WRITER,
                        TABLE_ACCOUNTS + "." + COL_ACCOUNTS_COLOR,
                        TABLE_ACCOUNTS + "." + COL_ACCOUNTS_PROVIDER_ID,
                        TABLE_PROVIDERS + "." + COL_PROVIDERS_HOST,
                        TABLE_PROVIDERS + "." + COL_PROVIDERS_NAME,
                        TABLE_PROVIDERS + "." + COL_PROVIDERS_API_TYPE,
                        TABLE_PROVIDERS + "." + COL_PROVIDERS_CONSUMER_KEY,
                        TABLE_PROVIDERS + "." + COL_PROVIDERS_CONSUMER_SECRET
                },
                null, null, null, null, null);
    }

    public void addAccount(AuthUserRecord aur) {
        ContentValues contentValues = aur.getContentValues();
        Cursor c = db.query(TABLE_ACCOUNTS, null, null, null, null, null, null);
        int count = c.getCount();
        c.close();
        if (count == 0) {
            contentValues.put(COL_ACCOUNTS_IS_PRIMARY, 1);
        }
        db.replaceOrThrow(TABLE_ACCOUNTS, null, contentValues);
    }

    public void updateAccountProfile(long providerId, long userId, String screenName, String name, String profileImageUrl) {
        ContentValues cv = new ContentValues();
        cv.put(COL_ACCOUNTS_USER_ID, userId);
        cv.put(COL_ACCOUNTS_SCREEN_NAME, screenName);
        cv.put(COL_ACCOUNTS_DISPLAY_NAME, name);
        cv.put(COL_ACCOUNTS_PROFILE_IMAGE_URL, profileImageUrl);

        if (providerId == Provider.TWITTER.getId()) {
            db.update(TABLE_ACCOUNTS, cv, COL_ACCOUNTS_PROVIDER_ID + " IS NULL AND " + COL_ACCOUNTS_USER_ID + "=" + userId, null);
        } else {
            db.update(TABLE_ACCOUNTS, cv, COL_ACCOUNTS_PROVIDER_ID + "=" + providerId + " AND " + COL_ACCOUNTS_USER_ID + "=" + userId, null);
        }
    }
    //</editor-fold>

    //<editor-fold desc="Drafts">
    public void updateDraft(StatusDraft draft) {
        beginTransaction();
        try {
            deleteDraft(draft);
            for (ContentValues values : draft.getContentValuesArray()) {
                db.insert(TABLE_DRAFTS, null, values);
            }
            setTransactionSuccessful();
        } finally {
            endTransaction();
        }
    }

    public void deleteDraft(StatusDraft draft) {
        deleteDraft(draft.getDateTime());
    }

    public void deleteDraft(long savedTime) {
        db.delete(TABLE_DRAFTS, COL_DRAFTS_DATETIME + "=" + savedTime, null);
    }

    public List<StatusDraft> getDrafts() {
        List<StatusDraft> draftList = new ArrayList<>();
        Cursor cursor = db.query(
                TABLE_DRAFTS +
                        " INNER JOIN " + TABLE_ACCOUNTS + " ON " + TABLE_DRAFTS + "." + COL_DRAFTS_WRITER_ID + "=" + TABLE_ACCOUNTS + "." + COL_ACCOUNTS_ID +
                        " LEFT JOIN " + TABLE_PROVIDERS + " ON " + TABLE_ACCOUNTS + "." + COL_ACCOUNTS_PROVIDER_ID + "=" + TABLE_PROVIDERS + "." + COL_PROVIDERS_ID,
                new String[]{
                        TABLE_DRAFTS + ".*",
                        TABLE_ACCOUNTS + "." + COL_ACCOUNTS_ID,
                        TABLE_ACCOUNTS + "." + COL_ACCOUNTS_USER_ID,
                        TABLE_ACCOUNTS + "." + COL_ACCOUNTS_SCREEN_NAME,
                        TABLE_ACCOUNTS + "." + COL_ACCOUNTS_DISPLAY_NAME,
                        TABLE_ACCOUNTS + "." + COL_ACCOUNTS_PROFILE_IMAGE_URL,
                        TABLE_ACCOUNTS + "." + COL_ACCOUNTS_ACCESS_TOKEN,
                        TABLE_ACCOUNTS + "." + COL_ACCOUNTS_ACCESS_TOKEN_SECRET,
                        TABLE_ACCOUNTS + "." + COL_ACCOUNTS_IS_PRIMARY,
                        TABLE_ACCOUNTS + "." + COL_ACCOUNTS_IS_WRITER,
                        TABLE_ACCOUNTS + "." + COL_ACCOUNTS_COLOR,
                        TABLE_ACCOUNTS + "." + COL_ACCOUNTS_PROVIDER_ID,
                        TABLE_PROVIDERS + "." + COL_PROVIDERS_HOST,
                        TABLE_PROVIDERS + "." + COL_PROVIDERS_NAME,
                        TABLE_PROVIDERS + "." + COL_PROVIDERS_API_TYPE,
                        TABLE_PROVIDERS + "." + COL_PROVIDERS_CONSUMER_KEY,
                        TABLE_PROVIDERS + "." + COL_PROVIDERS_CONSUMER_SECRET
                },
                null,
                null, null, null, COL_DRAFTS_DATETIME);
        try {
            StatusDraft last = null;
            StatusDraft draft;
            if (cursor.moveToFirst()) do {
                draft = new StatusDraft(cursor);
                if (last != null && last.getDateTime() == draft.getDateTime()) {
                    last.getWriters().add(new AuthUserRecord(cursor));
                }
                else {
                    draft.getWriters().add(new AuthUserRecord(cursor));
                    draftList.add(draft);
                    last = draft;
                }
            } while (cursor.moveToNext());
        } finally {
            cursor.close();
        }
        return draftList;
    }
    //</editor-fold>

    public ArrayList<TabInfo> getTabs() {
        Cursor cursor = db.rawQuery(
                "SELECT " +
                    TABLE_TABS + "." + COL_TABS_ID + " AS _id_t, " +
                    TABLE_TABS + ".*, " +
                    TABLE_ACCOUNTS + "." + COL_ACCOUNTS_ID + "," +
                    TABLE_ACCOUNTS + "." + COL_ACCOUNTS_USER_ID + "," +
                    TABLE_ACCOUNTS + "." + COL_ACCOUNTS_SCREEN_NAME + "," +
                    TABLE_ACCOUNTS + "." + COL_ACCOUNTS_DISPLAY_NAME + "," +
                    TABLE_ACCOUNTS + "." + COL_ACCOUNTS_PROFILE_IMAGE_URL + "," +
                    TABLE_ACCOUNTS + "." + COL_ACCOUNTS_ACCESS_TOKEN + "," +
                    TABLE_ACCOUNTS + "." + COL_ACCOUNTS_ACCESS_TOKEN_SECRET + "," +
                    TABLE_ACCOUNTS + "." + COL_ACCOUNTS_IS_PRIMARY + "," +
                    TABLE_ACCOUNTS + "." + COL_ACCOUNTS_IS_WRITER + "," +
                    TABLE_ACCOUNTS + "." + COL_ACCOUNTS_COLOR + "," +
                    TABLE_ACCOUNTS + "." + COL_ACCOUNTS_PROVIDER_ID + "," +
                    TABLE_PROVIDERS + "." + COL_PROVIDERS_HOST + "," +
                    TABLE_PROVIDERS + "." + COL_PROVIDERS_NAME + "," +
                    TABLE_PROVIDERS + "." + COL_PROVIDERS_API_TYPE + "," +
                    TABLE_PROVIDERS + "." + COL_PROVIDERS_CONSUMER_KEY + "," +
                    TABLE_PROVIDERS + "." + COL_PROVIDERS_CONSUMER_SECRET +
                " FROM " + TABLE_TABS +
                    " LEFT JOIN " + TABLE_ACCOUNTS +
                    " ON " + TABLE_TABS + "." + COL_TABS_BIND_ACCOUNT_ID + " = " + TABLE_ACCOUNTS + "." + COL_ACCOUNTS_ID +
                    " LEFT JOIN " + TABLE_PROVIDERS +
                    " ON " + TABLE_ACCOUNTS + "." + COL_ACCOUNTS_PROVIDER_ID + "=" + TABLE_PROVIDERS + "." + COL_PROVIDERS_ID +
                " ORDER BY " + COL_TABS_TAB_ORDER, null
        );
        ArrayList<TabInfo> tabs = new ArrayList<>();
        try {
            if (cursor.moveToFirst()) {
                do {
                    TabInfo info = new TabInfo(cursor);
                    tabs.add(info);
                } while (cursor.moveToNext());
            }
        } finally {
            cursor.close();
        }
        Iterator<TabInfo> iterator = tabs.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().isStartup()) {
                break;
            }
        }
        if (!tabs.isEmpty() && !tabs.get(tabs.size() - 1).isStartup() && !iterator.hasNext()) {
            tabs.get(0).setStartup(true);
        }
        return tabs;
    }

    //<editor-fold desc="SearchHistory">
    public List<SearchHistory> getSearchHistories() {
        List<SearchHistory> searchHistories = new ArrayList<>();
        Cursor cursor = db.query(TABLE_SEARCH_HISTORY, null, null, null, null, null, COL_SHISTORY_DATE + " DESC");
        try {
            if (cursor.moveToFirst()) do {
                searchHistories.add(new SearchHistory(cursor));
            } while (cursor.moveToNext());
        } finally {
            cursor.close();
        }
        return searchHistories;
    }

    public void updateSearchHistory(String query) {
        db.replace(TABLE_SEARCH_HISTORY, null,
                new SearchHistory(query, new Date(System.currentTimeMillis())).getContentValues());
        db.execSQL("DELETE FROM " + TABLE_SEARCH_HISTORY + " WHERE " + COL_SHISTORY_ID + " IN (" +
                        "SELECT " + COL_SHISTORY_ID + " FROM " + TABLE_SEARCH_HISTORY +
                        " ORDER BY " + COL_SHISTORY_DATE + " DESC LIMIT -1 OFFSET 10)"
        );
    }
    //</editor-fold>

    public void importMuteConfigs(List<MuteConfig> muteConfigs) {
        beginTransaction();
        try {
            db.delete(TABLE_MUTE, null, null);
            for (MuteConfig muteConfig : muteConfigs) {
                db.insert(TABLE_MUTE, null, muteConfig.getContentValues());
            }
            setTransactionSuccessful();
        } finally {
            endTransaction();
        }
    }

    public ArrayList<Bookmark> getBookmarks() {
        Cursor cursor = db.rawQuery(
                "SELECT " +
                        TABLE_BOOKMARKS + "." + COL_BOOKMARKS_ID + " AS _id_b, " +
                        TABLE_BOOKMARKS + ".*, " +
                        TABLE_ACCOUNTS + "." + COL_ACCOUNTS_ID + "," +
                        TABLE_ACCOUNTS + "." + COL_ACCOUNTS_USER_ID + "," +
                        TABLE_ACCOUNTS + "." + COL_ACCOUNTS_SCREEN_NAME + "," +
                        TABLE_ACCOUNTS + "." + COL_ACCOUNTS_DISPLAY_NAME + "," +
                        TABLE_ACCOUNTS + "." + COL_ACCOUNTS_PROFILE_IMAGE_URL + "," +
                        TABLE_ACCOUNTS + "." + COL_ACCOUNTS_ACCESS_TOKEN + "," +
                        TABLE_ACCOUNTS + "." + COL_ACCOUNTS_ACCESS_TOKEN_SECRET + "," +
                        TABLE_ACCOUNTS + "." + COL_ACCOUNTS_IS_PRIMARY + "," +
                        TABLE_ACCOUNTS + "." + COL_ACCOUNTS_IS_WRITER + "," +
                        TABLE_ACCOUNTS + "." + COL_ACCOUNTS_COLOR + "," +
                        TABLE_ACCOUNTS + "." + COL_ACCOUNTS_PROVIDER_ID + "," +
                        TABLE_PROVIDERS + "." + COL_PROVIDERS_HOST + "," +
                        TABLE_PROVIDERS + "." + COL_PROVIDERS_NAME + "," +
                        TABLE_PROVIDERS + "." + COL_PROVIDERS_API_TYPE + "," +
                        TABLE_PROVIDERS + "." + COL_PROVIDERS_CONSUMER_KEY + "," +
                        TABLE_PROVIDERS + "." + COL_PROVIDERS_CONSUMER_SECRET +
                    " FROM " + TABLE_BOOKMARKS +
                        " LEFT JOIN " + TABLE_ACCOUNTS +
                        " ON " + TABLE_BOOKMARKS + "." + COL_BOOKMARKS_RECEIVER_ID + " = " + TABLE_ACCOUNTS + "." + COL_ACCOUNTS_ID +
                        " LEFT JOIN " + TABLE_PROVIDERS +
                        " ON " + TABLE_ACCOUNTS + "." + COL_ACCOUNTS_PROVIDER_ID + "=" + TABLE_PROVIDERS + "." + COL_PROVIDERS_ID +
                    " ORDER BY " + TABLE_BOOKMARKS + "." + COL_BOOKMARKS_SAVE_DATE + " DESC", null
        );
        ArrayList<Bookmark> bookmarks = new ArrayList<>();
        try {
            if (cursor.moveToFirst()) {
                do {
                    //TODO: デシリアライズ時のt4jバージョン差によるクラッシュを回避する技です。こんなの辞められるようにしような。
                    try {
                        bookmarks.add(new Bookmark(cursor));
                    } catch (RuntimeException e) {
                        e.printStackTrace();
                    }
                }
                while (cursor.moveToNext());
            }
        } finally {
            cursor.close();
        }
        return bookmarks;
    }

    //<editor-fold desc="Common Interface">
    public <T extends DBRecord> List<T> getRecords(Class<T> tableClass) {
        return getRecords(tableClass, (String) null, null);
    }

    public <T extends DBRecord> List<T> getRecords(Class<T> tableClass, String selection, String[] selectionArgs) {
        List<T> records = new ArrayList<>();
        DBTable annotation = tableClass.getAnnotation(DBTable.class);
        if (annotation == null) {
            throw new RuntimeException(tableClass.getName() + " is not annotated DBTable.");
        }
        Cursor cursor = db.query(annotation.value(), null, selection, selectionArgs, null, null, null);
        try {
            Constructor<T> constructor = tableClass.getConstructor(Cursor.class);
            if (cursor.moveToFirst()) do {
                records.add(constructor.newInstance(cursor));
            } while (cursor.moveToNext());
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        } catch (InstantiationException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        } finally {
            cursor.close();
        }
        return records;
    }

    public <T extends DBRecord> List<T> getRecords(final Class<T> tableClass, final Class<?>[] constructorClasses, final Object... constructorArguments) {
        final List<T> records = new ArrayList<>();
        DBTable annotation = tableClass.getAnnotation(DBTable.class);
        if (annotation == null) {
            throw new RuntimeException(tableClass.getName() + " is not annotated DBTable.");
        }
        if (constructorClasses == null) {
            throw new IllegalArgumentException("constructorClasses == 0");
        }
        final Cursor cursor = db.query(annotation.value(), null, null, null, null, null, null);
        try {
            Object[] args = new ArrayList<Object>(constructorArguments.length + 1) {{
                add(cursor);
                addAll(Arrays.asList(constructorArguments));
            }}.toArray(new Object[constructorArguments.length + 1]);
            Class<?>[] classes = new ArrayList<Class<?>>(constructorClasses.length + 1) {{
                add(Cursor.class);
                addAll(Arrays.asList(constructorClasses));
            }}.toArray(new Class<?>[constructorClasses.length + 1]);
            Constructor<T> constructor = tableClass.getConstructor(classes);
            if (cursor.moveToFirst()) do {
                records.add(constructor.newInstance(args));
            } while (cursor.moveToNext());
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        } catch (InstantiationException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        } finally {
            cursor.close();
        }
        return records;
    }

    public void updateRecord(DBRecord record) {
        Class<? extends DBRecord> clz = record.getClass();
        DBTable annotation = clz.getAnnotation(DBTable.class);
        if (annotation == null) {
            throw new RuntimeException(clz.getName() + " is not annotated DBTable.");
        }
        db.replace(annotation.value(), null, record.getContentValues());
    }

    public <T extends DBRecord> void updateRecord(List<T> records) {
        if (records.isEmpty()) {
            return;
        }
        Class<? extends DBRecord> clz = records.get(0).getClass();
        DBTable annotation = clz.getAnnotation(DBTable.class);
        if (annotation == null) {
            throw new RuntimeException(clz.getName() + " is not annotated DBTable.");
        }
        beginTransaction();
        try {
            for (DBRecord record : records) {
                db.replace(annotation.value(), null, record.getContentValues());
            }
            setTransactionSuccessful();
        } finally {
            endTransaction();
        }
    }

    public <T extends DBRecord> void updateRecord(T[] records) {
        if (records.length < 1) {
            return;
        }
        Class<? extends DBRecord> clz = records[0].getClass();
        DBTable annotation = clz.getAnnotation(DBTable.class);
        if (annotation == null) {
            throw new RuntimeException(clz.getName() + " is not annotated DBTable.");
        }
        beginTransaction();
        try {
            for (DBRecord record : records) {
                db.replace(annotation.value(), null, record.getContentValues());
            }
            setTransactionSuccessful();
        } finally {
            endTransaction();
        }
    }

    public <T extends DBRecord> void importRecords(List<T> records) {
        if (records.isEmpty()) {
            return;
        }
        Class<? extends DBRecord> clz = records.get(0).getClass();
        DBTable annotation = clz.getAnnotation(DBTable.class);
        if (annotation == null) {
            throw new RuntimeException(clz.getName() + " is not annotated DBTable.");
        }
        beginTransaction();
        try {
            db.delete(annotation.value(), null, null);
            for (DBRecord record : records) {
                db.insert(annotation.value(), null, record.getContentValues());
            }
            setTransactionSuccessful();
        } finally {
            endTransaction();
        }
    }

    public void deleteRecord(DBRecord record) {
        Class<? extends DBRecord> clz = record.getClass();
        DBTable annotation = clz.getAnnotation(DBTable.class);
        if (annotation == null) {
            throw new RuntimeException(clz.getName() + " is not annotated DBTable.");
        }
        try {
            Long id = (Long) clz.getMethod(annotation.deleteKeyMethodName()).invoke(record);
            int affected = db.delete(annotation.value(), annotation.idColumnName() + "=" + id, null);
            if (affected == 0) {
                Log.w(CentralDatabase.class.getSimpleName(), "Record was not deleted!! Table = " + annotation.value() + ", ID = " + id);
            }
        } catch (InvocationTargetException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public void deleteRecord(Class<? extends DBRecord> clz, long id) {
        DBTable annotation = clz.getAnnotation(DBTable.class);
        if (annotation == null) {
            throw new RuntimeException(clz.getName() + " is not annotated DBTable.");
        }
        db.delete(annotation.value(), annotation.idColumnName() + "=" + id, null);
    }

    public List<Map> getRecordMaps(Class<? extends DBRecord> clz) {
        DBTable annotation = clz.getAnnotation(DBTable.class);
        if (annotation == null) {
            throw new RuntimeException(clz.getName() + " is not annotated DBTable.");
        }
        return getRecordMaps(annotation.value());
    }

    public List<Map> getRecordMaps(String tableName) {
        List<Map> result = new ArrayList<>();
        Cursor cursor = db.query(tableName, null, null, null, null, null, null);
        try {
            if (cursor.moveToFirst()) do {
                Map<String, Object> m = new HashMap<>();
                CursorWindow window = ((SQLiteCursor) cursor).getWindow();

                for (String column : cursor.getColumnNames()) {
                    int columnIndex = cursor.getColumnIndex(column);

                    if (window.isNull(cursor.getPosition(), columnIndex)) {
                        m.put(column, null);
                    } else if (window.isLong(cursor.getPosition(), columnIndex)) {
                        m.put(column, cursor.getLong(columnIndex));
                    } else if (window.isFloat(cursor.getPosition(), columnIndex)) {
                        m.put(column, cursor.getDouble(columnIndex));
                    } else if (window.isString(cursor.getPosition(), columnIndex)) {
                        m.put(column, cursor.getString(columnIndex));
                    } else if (window.isBlob(cursor.getPosition(), columnIndex)) {
                        m.put(column, cursor.getBlob(columnIndex));
                    }
                }

                result.add(m);
            } while (cursor.moveToNext());
        } finally {
            cursor.close();
        }
        return result;
    }

    public void importRecordMaps(Class<? extends DBRecord> clz, List<Map> records) {
        DBTable annotation = clz.getAnnotation(DBTable.class);
        if (annotation == null) {
            throw new RuntimeException(clz.getName() + " is not annotated DBTable.");
        }
        importRecordMaps(annotation.value(), records);
    }

    public void importRecordMaps(String tableName, List<Map> records) {
        db.delete(tableName, null, null);
        for (Map<String, ?> record : records) {
            ContentValues values = new ContentValues();

            for (Map.Entry<String, ?> entry : record.entrySet()) {
                if (entry.getValue() == null) {
                    values.putNull(entry.getKey());
                } else if (entry.getValue() instanceof Long || entry.getValue() instanceof Integer) {
                    values.put(entry.getKey(), (Long) entry.getValue());
                } else if (entry.getValue() instanceof Float || entry.getValue() instanceof Double) {
                    values.put(entry.getKey(), (Double) entry.getValue());
                } else if (entry.getValue() instanceof String) {
                    values.put(entry.getKey(), (String) entry.getValue());
                } else if (entry.getValue() instanceof List) {
                    // ここではbyte[]になりそこねたList<Long>が来ることのみを想定している

                    List byteList = (List) entry.getValue();
                    byte[] buf = new byte[byteList.size()];

                    for (int i = 0; i < buf.length; i++) {
                        long v = (long) byteList.get(i);
                        buf[i] = (byte) v;
                    }

                    values.put(entry.getKey(), buf);
                }
            }

            db.insertOrThrow(tableName, null, values);
        }
    }

    @Deprecated
    public Cursor rawQuery(String sql, String[] selectionArgs) {
        return db.rawQuery(sql, selectionArgs);
    }
    //</editor-fold>
}
