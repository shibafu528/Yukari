package shibafu.yukari.activity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.cursoradapter.widget.CursorAdapter;
import androidx.fragment.app.FragmentTransaction;

import java.util.Objects;

import shibafu.yukari.R;
import shibafu.yukari.activity.base.ActionBarYukariBase;
import shibafu.yukari.common.bitmapcache.ImageLoaderTask;
import shibafu.yukari.core.App;
import shibafu.yukari.database.AccountManager;
import shibafu.yukari.database.AuthUserRecord;
import shibafu.yukari.database.CentralDatabase;
import shibafu.yukari.database.DBUser;
import shibafu.yukari.database.Provider;
import shibafu.yukari.databinding.RowUserBinding;
import shibafu.yukari.fragment.TwitterUserListFragment;

/**
 * Created by shibafu on 14/06/01.
 */
public class UserSearchActivity extends ActionBarYukariBase {

    private LinearLayout tipsLayout;
    private Animation inAnim, outAnim;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_search);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);

        tipsLayout = (LinearLayout) findViewById(R.id.llFrameTitle);
        inAnim = AnimationUtils.loadAnimation(this, R.anim.activity_tweet_open_enter);
        outAnim = AnimationUtils.loadAnimation(this, R.anim.activity_tweet_close_exit);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.user_search, menu);
        MenuItem search = menu.findItem(R.id.action_search);
        search.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
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

        final SearchView searchView = (SearchView) search.getActionView();
        searchView.setQueryHint("ユーザーを検索");
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String s) {
                AuthUserRecord user = getPreferredUser();
                if (user == null) {
                    Toast.makeText(UserSearchActivity.this, "使用可能なTwitterアカウントが無いため、ユーザー検索を利用できません。", Toast.LENGTH_SHORT).show();
                    return true;
                }

                TwitterUserListFragment fragment = TwitterUserListFragment.newSearchInstance(user, "Search: " + s, s);

                FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
                transaction.replace(R.id.frame, fragment).commit();

                searchView.clearFocus();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String s) {
                if (s.length() < 1) {
                    tipsLayout.setVisibility(View.VISIBLE);
                    tipsLayout.startAnimation(inAnim);
                }
                else if (tipsLayout.getVisibility() == View.VISIBLE) {
                    tipsLayout.setVisibility(View.GONE);
                    tipsLayout.startAnimation(outAnim);
                }
                return true;
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
                if (c.getLong(c.getColumnIndexOrThrow("_id")) > -1) {
                    DBUser user = new DBUser(c);
                    screenName = user.getScreenName();
                }
                else {
                    screenName = c.getString(1).replaceAll("[@＠]", "");
                }
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(searchView.getWindowToken(), 0);

                Intent intent = ProfileActivity.newIntent(UserSearchActivity.this,
                        getPreferredUser(),
                        Uri.parse("http://twitter.com/" + screenName));
                startActivity(intent);
                return true;
            }
        });
        searchView.setSuggestionsAdapter(new UserSuggestionAdapter(this));
        search.expandActionView();

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Nullable
    private AuthUserRecord getPreferredUser() {
        AccountManager am = App.getInstance(this).getAccountManager();
        AuthUserRecord user = am.getPrimaryUser();
        if (user == null || user.Provider.getApiType() != Provider.API_TWITTER) {
            for (AuthUserRecord userRecord : am.getUsers()) {
                if (userRecord.Provider.getApiType() == Provider.API_TWITTER) {
                    user = userRecord;
                    break;
                }
            }
        }
        return user;
    }

    private class UserSuggestionAdapter extends CursorAdapter {
        private final LayoutInflater inflater;

        public UserSuggestionAdapter(Context context) {
            super(context, null, false);
            inflater = (LayoutInflater) context.getSystemService(LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            RowUserBinding binding = RowUserBinding.inflate(inflater, parent, false);
            View v = binding.getRoot();
            v.setBackgroundResource(R.drawable.selector_key_light_background);
            binding.userName.setTextColor(Color.WHITE);
            binding.userSn.setTextColor(Color.WHITE);
            v.setTag(binding);
            bindView(v, context, cursor);
            return v;
        }

        @SuppressLint("SetTextI18n")
        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            RowUserBinding binding = (RowUserBinding) view.getTag();
            if (cursor.getLong(cursor.getColumnIndexOrThrow("_id")) > -1) {
                DBUser user = new DBUser(cursor);
                binding.userName.setText(user.getName());
                binding.userSn.setText("@" + user.getScreenName());
                ImageLoaderTask.loadProfileIcon(getApplicationContext(), binding.userIcon, user.getProfileImageURLHttps());
            } else {
                binding.userIcon.setImageResource(R.drawable.ic_profile);
                binding.userName.setText("プロフィールを表示");
                binding.userSn.setText(cursor.getString(1));
            }
        }

        @Override
        public Cursor runQueryOnBackgroundThread(CharSequence constraint) {
            if (!TextUtils.isEmpty(constraint) && (constraint.charAt(0) == '@' || constraint.charAt(0) == '＠')) {
                MatrixCursor cursor = new MatrixCursor(new String[] {"_id", "text"});
                cursor.addRow(new Object[] {-1, constraint});
                return cursor;
            }
            else if (!TextUtils.isEmpty(constraint)) {
                String st = "%" + constraint + "%";
                return App.getInstance(getApplicationContext()).getDatabase().getUsersCursor(
                        CentralDatabase.COL_USER_NAME + " LIKE ? OR " + CentralDatabase.COL_USER_SCREEN_NAME + " LIKE ?",
                        new String[]{st, st});
            }
            return super.runQueryOnBackgroundThread(constraint);
        }
    }
}
