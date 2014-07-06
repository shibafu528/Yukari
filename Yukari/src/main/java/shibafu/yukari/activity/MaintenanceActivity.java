package shibafu.yukari.activity;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.ListFragment;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

import butterknife.ButterKnife;
import butterknife.InjectView;
import butterknife.OnClick;
import shibafu.yukari.R;
import shibafu.yukari.common.async.SimpleAsyncTask;
import shibafu.yukari.common.async.ThrowableTwitterAsyncTask;
import shibafu.yukari.common.bitmapcache.BitmapCache;
import shibafu.yukari.common.bitmapcache.ImageLoaderTask;
import shibafu.yukari.database.CentralDatabase;
import shibafu.yukari.service.TwitterService;
import shibafu.yukari.service.TwitterServiceDelegate;
import shibafu.yukari.twitter.AuthUserRecord;
import twitter4j.RateLimitStatus;
import twitter4j.Twitter;
import twitter4j.TwitterException;

/**
 * Created by shibafu on 14/07/05.
 */
public class MaintenanceActivity extends ActionBarActivity implements TwitterServiceDelegate{

    private TwitterService service;
    private boolean serviceBound = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_parent);
        ActionBar actionBar = getSupportActionBar();
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

        actionBar.addTab(actionBar.newTab()
                .setText("API Status")
                .setTabListener(new TabListener<>(this, "api", ApiMaintenanceFragment.class)));
        actionBar.addTab(actionBar.newTab()
                .setText("Database")
                .setTabListener(new TabListener<>(this, "db", DBMaintenanceFragment.class))
        );
    }

    @Override
    protected void onStart() {
        super.onStart();
        bindService(new Intent(this, TwitterService.class), connection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        service.getStatusManager().startAsync();
        unbindService(connection);
    }

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            TwitterService.TweetReceiverBinder binder = (TwitterService.TweetReceiverBinder) service;
            MaintenanceActivity.this.service = binder.getService();
            serviceBound = true;

            for (Fragment fragment : getSupportFragmentManager().getFragments()) {
                if (fragment.isResumed()) {
                    ((ServiceConnectable) fragment).onServiceConnected();
                }
            }

            MaintenanceActivity.this.service.getStatusManager().stopAsync();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
        }
    };

    @Override
    public TwitterService getTwitterService() {
        return service;
    }

    private class TabListener<T extends Fragment> implements ActionBar.TabListener {
        private Fragment fragment;
        private Activity activity;
        private String tag;
        private Class<T> cls;

        private TabListener(Activity activity, String tag, Class<T> cls) {
            this.activity = activity;
            this.tag = tag;
            this.cls = cls;
            this.fragment = getSupportFragmentManager().findFragmentByTag(tag);
        }

        @Override
        public void onTabSelected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
            if (fragment == null) {
                fragment = Fragment.instantiate(activity, cls.getName());
                fragmentTransaction.add(R.id.frame, fragment, tag);
            } else if (fragment.isDetached()) {
                fragmentTransaction.attach(fragment);
            }
        }

        @Override
        public void onTabUnselected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
            if (fragment != null) {
                fragmentTransaction.detach(fragment);
            }
        }

        @Override
        public void onTabReselected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {

        }
    }

    private interface ServiceConnectable {
        void onServiceConnected();
        TwitterService getService();
        boolean serviceBound();
    }

    public static class DBMaintenanceFragment extends Fragment implements ServiceConnectable{

        @InjectView(R.id.tvYdbName) TextView title;
        @InjectView(R.id.tvYdbSize) TextView size;
        @InjectView(R.id.tvYdbUserEnt) TextView usersCount;

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View v = inflater.inflate(R.layout.fragment_dbmt, container, false);
            ButterKnife.inject(this, v);

            title.setText(String.format("%s, version %d", CentralDatabase.DB_FILENAME, CentralDatabase.DB_VER));
            size.setText(Formatter.formatFileSize(getActivity(), getActivity().getDatabasePath(CentralDatabase.DB_FILENAME).length()));
            return v;
        }

        @Override
        public void onDestroyView() {
            super.onDestroyView();
            ButterKnife.reset(this);
        }

        @OnClick(R.id.btnYdbWipe)
        public void onClickWipe() {
            new SimpleAsyncTask() {

                private SimpleProgressDialogFragment fragment;

                @Override
                protected Void doInBackground(Void... params) {
                    getService().getDatabase().wipeUsers();
                    return null;
                }

                @Override
                protected void onPreExecute() {
                    super.onPreExecute();
                    fragment = SimpleProgressDialogFragment.newInstance(
                            null, "Wipe User table...", true, false
                    );
                    fragment.show(getFragmentManager(), "wipe");
                }

                @Override
                protected void onPostExecute(Void aVoid) {
                    super.onPostExecute(aVoid);
                    fragment.dismiss();
                    onServiceConnected();
                }
            }.execute();
        }

        @OnClick(R.id.btnYdbVacuum)
        public void onClickVacuum() {
            new SimpleAsyncTask() {

                private SimpleProgressDialogFragment fragment;

                @Override
                protected Void doInBackground(Void... params) {
                    getService().getDatabase().vacuum();
                    return null;
                }

                @Override
                protected void onPreExecute() {
                    super.onPreExecute();
                    fragment = SimpleProgressDialogFragment.newInstance(
                            null, "Vacuum database...", true, false
                    );
                    fragment.show(getFragmentManager(), "vacuum");
                }

                @Override
                protected void onPostExecute(Void aVoid) {
                    super.onPostExecute(aVoid);
                    fragment.dismiss();
                    onServiceConnected();
                }
            }.execute();
        }

        @Override
        public void onResume() {
            super.onResume();
            if (serviceBound()) {
                onServiceConnected();
            }
        }

        @Override
        public void onServiceConnected() {
            usersCount.setText(String.format("%d entries", getService().getDatabase().getUsersCursor().getCount()));
            size.setText(Formatter.formatFileSize(getActivity(), getActivity().getDatabasePath(CentralDatabase.DB_FILENAME).length()));
        }

        @Override
        public TwitterService getService() {
            return ((MaintenanceActivity)getActivity()).getTwitterService();
        }

        @Override
        public boolean serviceBound() {
            return ((MaintenanceActivity)getActivity()).serviceBound;
        }
    }

    public static class ApiMaintenanceFragment extends ListFragment implements ServiceConnectable {

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
                        new ImageLoaderTask.DrawableLoaderCallback() {
                            @Override
                            public void onLoadDrawable(Drawable drawable) {
                                accountMenu.setIcon(drawable);
                            }
                        }
                );
                ThrowableTwitterAsyncTask<AuthUserRecord, Map<String, RateLimitStatus>> task
                        = new ThrowableTwitterAsyncTask<AuthUserRecord, Map<String, RateLimitStatus>>() {
                    @Override
                    protected void showToast(String message) {
                        Toast.makeText(getActivity(), message, Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    protected ThrowableResult<Map<String, RateLimitStatus>> doInBackground(AuthUserRecord... params) {
                        Twitter twitter = getService().getTwitter();
                        twitter.setOAuthAccessToken(params[0].getAccessToken());
                        try {
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
                        if (!result.isException() && result.getResult() != null) {
                            ApiAdapter apiAdapter = new ApiAdapter(getActivity(), result.getResult());
                            setListAdapter(apiAdapter);
                            setListShown(true);
                        }
                    }
                };
                task.executeIf(currentUser);
            }
        }

        @Override
        public void onResume() {
            super.onResume();
            if (serviceBound()) {
                reload();
            }
        }

        @Override
        public void onServiceConnected() {
            currentUser = getService().getPrimaryUser();
            reload();
        }

        @Override
        public TwitterService getService() {
            return ((MaintenanceActivity)getActivity()).getTwitterService();
        }

        @Override
        public boolean serviceBound() {
            return ((MaintenanceActivity)getActivity()).serviceBound;
        }

        class ApiAdapter extends BaseAdapter {
            private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
            private LayoutInflater inflater;
            private String[] keys;
            private RateLimitStatus[] rateLimitStatuses;

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
                ViewHolder vh;
                if (convertView == null) {
                    convertView = inflater.inflate(R.layout.row_api, null);
                    vh = new ViewHolder(convertView);
                    convertView.setTag(vh);
                } else {
                    vh = (ViewHolder) convertView.getTag();
                }
                RateLimitStatus status = getItem(position);
                if (status != null) {
                    vh.apiEndPoint.setText(keys[position]);
                    vh.apiLimit.setText(String.format("Limit: %d/%d", status.getRemaining(), status.getLimit()));
                    vh.apiReset.setText("Reset: " + sdf.format(new Date((long)status.getResetTimeInSeconds() * 1000)));
                    vh.progressBar.setProgress(status.getRemaining() * 100 / status.getLimit());
                }
                return convertView;
            }

            class ViewHolder {
                @InjectView(R.id.tvApiEndpoint) TextView apiEndPoint;
                @InjectView(R.id.tvApiLimit) TextView apiLimit;
                @InjectView(R.id.tvApiReset) TextView apiReset;
                @InjectView(R.id.progressBar) ProgressBar progressBar;

                ViewHolder(View v) {
                    ButterKnife.inject(this, v);
                }
            }
        }
    }

    public static class SimpleProgressDialogFragment extends DialogFragment {

        public static SimpleProgressDialogFragment newInstance(String title, String message, boolean indeterminate, boolean isCancellable) {
            SimpleProgressDialogFragment fragment = new SimpleProgressDialogFragment();
            Bundle args = new Bundle();
            args.putString("title", title);
            args.putString("message", message);
            args.putBoolean("indeterminate", indeterminate);
            fragment.setArguments(args);
            fragment.setCancelable(isCancellable);
            return fragment;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            Bundle args = getArguments();
            return ProgressDialog.show(getActivity(),
                    args.getString("title"),
                    args.getString("message"),
                    args.getBoolean("indeterminate"));
        }
    }
}
