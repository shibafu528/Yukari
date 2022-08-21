package shibafu.yukari.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Toast;

import androidx.core.view.MenuItemCompat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

import shibafu.yukari.R;
import shibafu.yukari.activity.base.ActionBarYukariBase;
import shibafu.yukari.common.async.ThrowableTwitterAsyncTask;
import shibafu.yukari.common.bitmapcache.BitmapCache;
import shibafu.yukari.common.bitmapcache.ImageLoaderTask;
import shibafu.yukari.database.AuthUserRecord;
import shibafu.yukari.database.Provider;
import shibafu.yukari.databinding.RowApiBinding;
import shibafu.yukari.fragment.base.ListYukariBaseFragment;
import twitter4j.RateLimitStatus;
import twitter4j.Twitter;
import twitter4j.TwitterException;

public class TwitterRateLimitStatusActivity extends ActionBarYukariBase {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_parent);

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.frame, new ApiMaintenanceFragment())
                    .commit();
        }
    }

    @Override
    public void onServiceConnected() {}

    @Override
    public void onServiceDisconnected() {}

    public static class ApiMaintenanceFragment extends ListYukariBaseFragment {

        private static final int REQUEST_CHOOSE = 1;
        private MenuItem accountMenu;
        private AuthUserRecord currentUser;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setHasOptionsMenu(true);
        }

        @Override
        public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
            accountMenu = menu.add(Menu.NONE, 0, Menu.NONE, "Account").setIcon(R.drawable.yukatterload);
            MenuItemCompat.setShowAsAction(accountMenu, MenuItemCompat.SHOW_AS_ACTION_ALWAYS);
            reload();
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            switch (item.getItemId()) {
                case 0:
                    startActivityForResult(new Intent(getActivity(), AccountChooserActivity.class), REQUEST_CHOOSE);
                    return true;
            }
            return super.onOptionsItemSelected(item);
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            if (resultCode == RESULT_OK) {
                switch (requestCode) {
                    case REQUEST_CHOOSE:
                        currentUser = (AuthUserRecord) data.getSerializableExtra(AccountChooserActivity.EXTRA_SELECTED_RECORD);
                        reload();
                        break;
                }
            }
        }

        private void reload() {
            if (currentUser != null && accountMenu != null) {
                ImageLoaderTask.loadDrawable(
                        getActivity(),
                        currentUser.ProfileImageUrl,
                        BitmapCache.PROFILE_ICON_CACHE,
                        accountMenu::setIcon
                );
                ThrowableTwitterAsyncTask<AuthUserRecord, Map<String, RateLimitStatus>> task
                        = new ThrowableTwitterAsyncTask<AuthUserRecord, Map<String, RateLimitStatus>>() {
                    @Override
                    protected void showToast(String message) {
                        Toast.makeText(getActivity(), message, Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    protected ThrowableResult<Map<String, RateLimitStatus>> doInBackground(AuthUserRecord... params) {
                        try {
                            Twitter twitter = getTwitterService().getTwitterOrThrow(params[0]);
                            return new ThrowableResult<>(twitter.getRateLimitStatus());
                        } catch (TwitterException e) {
                            e.printStackTrace();
                            return new ThrowableResult<>(e);
                        }
                    }

                    @Override
                    protected void onPreExecute() {
                        super.onPreExecute();
                        setListShown(false);
                    }

                    @Override
                    protected void onPostExecute(ThrowableResult<Map<String, RateLimitStatus>> result) {
                        super.onPostExecute(result);
                        if (!result.isException() && result.getResult() != null && isAdded()) {
                            ApiAdapter apiAdapter = new ApiAdapter(getActivity(), result.getResult());
                            setListAdapter(apiAdapter);
                            setListShown(true);
                        }
                    }
                };
                task.executeParallel(currentUser);
            }
        }

        @Override
        public void onResume() {
            super.onResume();
            if (isTwitterServiceBound()) {
                if (currentUser == null) {
                    currentUser = findPrimaryTwitterUser();
                }
                reload();
            }
        }

        @Override
        public void onServiceConnected() {
            currentUser = findPrimaryTwitterUser();
            reload();
        }

        private AuthUserRecord findPrimaryTwitterUser() {
            AuthUserRecord userRecord = getTwitterService().getPrimaryUser();
            if (userRecord != null && userRecord.Provider.getApiType() != Provider.API_TWITTER) {
                for (AuthUserRecord user : getTwitterService().getUsers()) {
                    if (user.Provider.getApiType() == Provider.API_TWITTER) {
                        userRecord = user;
                        break;
                    }
                }
            }
            return userRecord;
        }

        @Override
        public void onServiceDisconnected() {}

        private static class ApiAdapter extends BaseAdapter {
            private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
            private final LayoutInflater inflater;
            private final String[] keys;
            private final RateLimitStatus[] rateLimitStatuses;

            ApiAdapter(Context context, Map<String, RateLimitStatus> rateLimitStatus) {
                keys = rateLimitStatus.keySet().toArray(new String[rateLimitStatus.size()]);
                rateLimitStatuses = rateLimitStatus.values().toArray(new RateLimitStatus[rateLimitStatus.size()]);
                this.inflater = (LayoutInflater) context.getSystemService(LAYOUT_INFLATER_SERVICE);
            }

            @Override
            public int getCount() {
                return rateLimitStatuses.length;
            }

            @Override
            public RateLimitStatus getItem(int position) {
                return rateLimitStatuses[position];
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                RowApiBinding vh;
                if (convertView == null) {
                    vh = RowApiBinding.inflate(inflater);
                    convertView = vh.getRoot();
                    convertView.setTag(vh);
                } else {
                    vh = (RowApiBinding) convertView.getTag();
                }
                RateLimitStatus status = getItem(position);
                if (status != null) {
                    vh.tvApiEndpoint.setText(keys[position]);
                    vh.tvApiLimit.setText(String.format("Limit: %d/%d", status.getRemaining(), status.getLimit()));
                    vh.tvApiReset.setText("Reset: " + sdf.format(new Date((long)status.getResetTimeInSeconds() * 1000)));
                    vh.progressBar.setProgress(status.getRemaining() * 100 / status.getLimit());
                }
                return convertView;
            }
        }
    }
}
