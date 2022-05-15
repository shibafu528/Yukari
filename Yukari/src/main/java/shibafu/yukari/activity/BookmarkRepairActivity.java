package shibafu.yukari.activity;

import android.annotation.SuppressLint;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import shibafu.yukari.activity.base.ActionBarYukariBase;
import shibafu.yukari.common.async.ParallelAsyncTask;
import shibafu.yukari.database.AuthUserRecord;
import shibafu.yukari.database.Bookmark;
import shibafu.yukari.database.CentralDatabase;
import shibafu.yukari.databinding.ActivityAssetBinding;
import shibafu.yukari.twitter.entity.TwitterStatus;
import twitter4j.Twitter;
import twitter4j.TwitterException;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Created by shibafu on 15/03/31.
 */
public class BookmarkRepairActivity extends ActionBarYukariBase {
    private ActivityAssetBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAssetBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.textView.setText("破損したブックマークを修復しています...");
        binding.tvProgress.setText("0 破損していたブックマーク\n0 修復成功\n0 エラー");
        binding.progressBar.setIndeterminate(true);
    }

    @Override
    public void onBackPressed() {}

    @Override
    public void onServiceConnected() {
        new RepairAsyncTask(this).executeParallel();
    }

    @Override
    public void onServiceDisconnected() {}

    private static class RepairAsyncTask extends ParallelAsyncTask<Void, Integer, Integer[]> {
        @SuppressLint("StaticFieldLeak")
        private final BookmarkRepairActivity activity;

        public RepairAsyncTask(BookmarkRepairActivity activity) {
            this.activity = activity;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
            activity.binding.tvProgress.setText(String.format("%d 破損していたブックマーク\n%d 修復成功\n%d エラー", values[0], values[1], values[2]));
        }

        @Override
        protected Integer[] doInBackground(Void... params) {
            int processedCount = 0;
            int repairCount = 0;
            int failedCount = 0;
            List<AuthUserRecord> userRecords = activity.getTwitterService().getUsers();
            CentralDatabase database = activity.getTwitterService().getDatabase();
            List<Long> healthyIds = new ArrayList<>(); // 問題ないレコードのID
            Map<Long, Long> mismatchedStatusIdByDatabaseId = new HashMap<>(); // DB上のIDとデシリアライズしたStatusのIDが合わないもの
            for (Bookmark aliveBookmark : database.getBookmarks()) {
                long idInDatabase = aliveBookmark.getIdInDatabase();
                long actualStatusId = aliveBookmark.getId();
                if (actualStatusId == idInDatabase) {
                    healthyIds.add(idInDatabase);
                } else {
                    mismatchedStatusIdByDatabaseId.put(idInDatabase, actualStatusId);
                }
            }
            Cursor cursor = database.rawQuery("select * from " + CentralDatabase.TABLE_BOOKMARKS, null);
            try {
                if (cursor.moveToFirst()) {
                    do {
                        publishProgress(processedCount, repairCount, failedCount);
                        long id = cursor.getLong(cursor.getColumnIndexOrThrow(CentralDatabase.COL_BOOKMARKS_ID));
                        if (healthyIds.contains(id)) {
                            Log.d("BookmarkRepairActivity", String.format(Locale.US, "%d: healthy", id));
                            continue;
                        }
                        if (mismatchedStatusIdByDatabaseId.containsKey(id)) {
                            Log.d("BookmarkRepairActivity", String.format(Locale.US, "%d: unhealthy (id mismatch)", id));
                        } else {
                            Log.d("BookmarkRepairActivity", String.format(Locale.US, "%d: unhealthy (deserialize failed)", id));
                        }
                        ++processedCount;

                        long receiverId = cursor.getLong(cursor.getColumnIndexOrThrow(CentralDatabase.COL_BOOKMARKS_RECEIVER_ID));
                        AuthUserRecord userRecord = null;
                        for (AuthUserRecord record : userRecords) {
                            if (record.InternalId == receiverId) {
                                userRecord = record;
                            }
                        }
                        if (userRecord == null) {
                            Log.d("BookmarkRepairActivity", String.format(Locale.US, "%d: receiver user not found", id));
                            ++failedCount;
                            continue;
                        }

                        Twitter twitter = activity.getTwitterService().getTwitter(userRecord);
                        if (twitter == null) {
                            Log.d("BookmarkRepairActivity", String.format(Locale.US, "%d: failed to initialize twitter api client", id));
                            ++failedCount;
                            continue;
                        }
                        try {
                            long statusId = mismatchedStatusIdByDatabaseId.getOrDefault(id, id);
                            Bookmark bookmark = new Bookmark(new TwitterStatus(twitter.showStatus(statusId), userRecord));
                            if (mismatchedStatusIdByDatabaseId.containsKey(id)) {
                                database.rawQuery("DELETE FROM " + CentralDatabase.TABLE_BOOKMARKS + " WHERE " + CentralDatabase.COL_BOOKMARKS_ID + " = " + id, null).moveToFirst();
                            }
                            database.updateRecord(bookmark);
                            ++repairCount;
                        } catch (TwitterException e) {
                            e.printStackTrace();
                            ++failedCount;
                        }
                    } while (cursor.moveToNext());
                }
            } finally {
                cursor.close();
            }
            return new Integer[]{processedCount, repairCount, failedCount};
        }

        @Override
        protected void onPostExecute(Integer[] values) {
            super.onPostExecute(values);
            Toast.makeText(activity.getApplicationContext(),
                    String.format("修復が終了しました\n========\n%d 破損していたブックマーク\n%d 修復成功\n%d エラー",
                            values[0], values[1], values[2]),
                    Toast.LENGTH_LONG).show();
            activity.finish();
        }
    }
}
