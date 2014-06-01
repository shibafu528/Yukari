package shibafu.yukari.activity;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.widget.CursorAdapter;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.SearchView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import shibafu.yukari.R;
import shibafu.yukari.common.bitmapcache.ImageLoaderTask;
import shibafu.yukari.database.CentralDatabase;
import shibafu.yukari.database.DBUser;
import shibafu.yukari.service.TwitterService;

/**
 * Created by shibafu on 14/06/01.
 */
public class UserSearchActivity extends ActionBarActivity {
    private TwitterService service;
    private boolean serviceBound = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_parent);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.user_search, menu);
        MenuItem search = menu.findItem(R.id.action_search);
        MenuItemCompat.setOnActionExpandListener(search, new MenuItemCompat.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                finish();
                return true;
            }
        });

        final SearchView searchView = (SearchView) MenuItemCompat.getActionView(search);
        searchView.setQueryHint("ユーザーを検索");
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String s) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String s) {
                return false;
            }
        });
        searchView.setOnSuggestionListener(new SearchView.OnSuggestionListener() {
            @Override
            public boolean onSuggestionSelect(int i) {
                return true;
            }

            @Override
            public boolean onSuggestionClick(int i) {
                CursorAdapter adapter = searchView.getSuggestionsAdapter();
                Cursor c = (Cursor) adapter.getItem(i);
                String screenName;
                if (c.getLong(c.getColumnIndex("_id")) > -1) {
                    DBUser user = new DBUser(c);
                    screenName = user.getScreenName();
                }
                else {
                    screenName = c.getString(1).replace("@", "");
                }
                Intent intent = new Intent(UserSearchActivity.this, ProfileActivity.class);
                intent.setData(Uri.parse("http://twitter.com/" + screenName));
                intent.putExtra(ProfileActivity.EXTRA_USER, serviceBound?service.getPrimaryUser():null);
                startActivity(intent);
                return true;
            }
        });
        searchView.setSuggestionsAdapter(new UserSuggestionAdapter(this));
        MenuItemCompat.expandActionView(search);

        return super.onCreateOptionsMenu(menu);
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
    protected void onStart() {
        super.onStart();
        bindService(new Intent(this, TwitterService.class), connection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        unbindService(connection);
    }

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            TwitterService.TweetReceiverBinder binder = (TwitterService.TweetReceiverBinder) service;
            UserSearchActivity.this.service = binder.getService();
            serviceBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
        }
    };

    private class UserSuggestionAdapter extends CursorAdapter {
        private LayoutInflater inflater;

        public UserSuggestionAdapter(Context context) {
            super(context, null, false);
            inflater = (LayoutInflater) context.getSystemService(LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            View v = inflater.inflate(R.layout.row_user, parent, false);
            v.setBackgroundResource(R.drawable.selector_key_light_background);
            ViewHolder vh = new ViewHolder();
            vh.ivIcon = (ImageView) v.findViewById(R.id.user_icon);
            vh.tvName = (TextView) v.findViewById(R.id.user_name);
            vh.tvScreenName = (TextView) v.findViewById(R.id.user_sn);
            v.setTag(vh);
            bindView(v, context, cursor);
            return v;
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            ViewHolder vh = (ViewHolder) view.getTag();
            if (cursor.getLong(cursor.getColumnIndex("_id")) > -1) {
                DBUser user = new DBUser(cursor);
                vh.tvName.setText(user.getName());
                vh.tvScreenName.setText("@" + user.getScreenName());
                ImageLoaderTask.loadProfileIcon(UserSearchActivity.this, vh.ivIcon, user.getProfileImageURLHttps());
            }
            else {
                vh.ivIcon.setImageResource(R.drawable.ic_profile);
                vh.tvName.setText("プロフィールを表示");
                vh.tvScreenName.setText(cursor.getString(1));
            }
        }

        @Override
        public Cursor runQueryOnBackgroundThread(CharSequence constraint) {
            if (!TextUtils.isEmpty(constraint) && constraint.charAt(0) == '@') {
                MatrixCursor cursor = new MatrixCursor(new String[] {"_id", "text"});
                cursor.addRow(new Object[] {-1, constraint});
                return cursor;
            }
            else if (serviceBound && !TextUtils.isEmpty(constraint)) {
                String st = "%" + constraint + "%";
                return service.getDatabase().getUsersCursor(
                        CentralDatabase.COL_USER_NAME + " LIKE ? OR " + CentralDatabase.COL_USER_SCREEN_NAME + " LIKE ?",
                        new String[]{st, st});
            }
            return super.runQueryOnBackgroundThread(constraint);
        }

        private class ViewHolder {
            ImageView ivIcon;
            TextView tvScreenName;
            TextView tvName;
        }
    }
}
