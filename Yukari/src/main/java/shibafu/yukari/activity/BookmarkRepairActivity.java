package shibafu.yukari.activity;

import android.database.Cursor;
import android.os.Bundle;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import butterknife.ButterKnife;
import butterknife.InjectView;
import shibafu.yukari.af2015.R;
import shibafu.yukari.activity.base.ActionBarYukariBase;
import shibafu.yukari.common.async.ParallelAsyncTask;
import shibafu.yukari.database.Bookmark;
import shibafu.yukari.database.CentralDatabase;
import shibafu.yukari.twitter.AuthUserRecord;
import shibafu.yukari.twitter.statusimpl.PreformedStatus;
import twitter4j.Twitter;
import twitter4j.TwitterException;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by shibafu on 15/03/31.
 */
public class BookmarkRepairActivity extends ActionBarYukariBase {
    @InjectView(R.id.textView)      TextView    textView;
    @InjectView(R.id.tvProgress)    TextView    progressLabel;
    @InjectView(R.id.progressBar)   ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_asset);
        ButterKnife.inject(this);
        textView.setText("破損したブックマークを修復しています...");
        progressLabel.setText("0 破損していたブックマーク\n0 修復成功\n0 エラー");
        progressBar.setIndeterminate(true);
    }

    @Override
    public void onBackPressed() {}

    @Override
    public void onServiceConnected() {
        ParallelAsyncTask<Void, Integer, Integer[]> task = new ParallelAsyncTask<Void, Integer, Integer[]>() {
            @Override
            protected void onProgressUpdate(Integer... values) {
                super.onProgressUpdate(values);
                progressLabel.setText(String.format("%d 破損していたブックマーク\n%d 修復成功\n%d エラー", values[0], values[1], values[2]));
            }

            @Override
            protected Integer[] doInBackground(Void... params) {
                int processedCount = 0;
                int repairCount = 0;
                int failedCount = 0;
                List<AuthUserRecord> userRecords = getTwitterService().getUsers();
                CentralDatabase database = getTwitterService().getDatabase();
                List<Long> aliveIDs = new ArrayList<>();
                for (Bookmark aliveBookmark : database.getBookmarks()) {
                    aliveIDs.add(aliveBookmark.getId());
                }
                Cursor cursor = database.rawQuery("select * from " + CentralDatabase.TABLE_BOOKMARKS, null);
                try {
                    if (cursor.moveToFirst()) {
                        do {
                            publishProgress(processedCount, repairCount, failedCount);
                            long id = cursor.getLong(cursor.getColumnIndex(CentralDatabase.COL_BOOKMARKS_ID));
                            if (aliveIDs.contains(id)) {
                                continue;
                            }
                            ++processedCount;

                            long receiverId = cursor.getLong(cursor.getColumnIndex(CentralDatabase.COL_BOOKMARKS_RECEIVER_ID));
                            AuthUserRecord userRecord = null;
                            for (AuthUserRecord record : userRecords) {
                                if (record.NumericId == receiverId) {
                                    userRecord = record;
                                }
                            }
                            if (userRecord != null) {
                                Twitter twitter = getTwitterService().getTwitter(userRecord);
                                if (twitter == null) {
                                    ++failedCount;
                                    continue;
                                }
                                try {
                                    Bookmark bookmark = new Bookmark(new PreformedStatus(twitter.showStatus(id), userRecord));
                                    database.updateRecord(bookmark);
                                    ++repairCount;
                                } catch (TwitterException e) {
                                    e.printStackTrace();
                                    ++failedCount;
                                }
                            } else {
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
                Toast.makeText(getApplicationContext(),
                        String.format("修復が終了しました\n========\n%d 破損していたブックマーク\n%d 修復成功\n%d エラー",
                                values[0], values[1], values[2]),
                        Toast.LENGTH_LONG).show();
                finish();
            }
        };
        task.executeParallel();
    }

    @Override
    public void onServiceDisconnected() {}
}
