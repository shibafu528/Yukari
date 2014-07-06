package shibafu.yukari.activity;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import butterknife.ButterKnife;
import butterknife.InjectView;
import butterknife.OnClick;
import shibafu.yukari.R;
import shibafu.yukari.common.async.SimpleAsyncTask;
import shibafu.yukari.database.CentralDatabase;
import shibafu.yukari.service.TwitterService;
import shibafu.yukari.service.TwitterServiceDelegate;

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
                ((ServiceConnectable)fragment).onServiceConnected();
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
        }

        @Override
        public void onTabSelected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
            if (fragment == null) {
                fragment = Fragment.instantiate(activity, cls.getName());
                fragmentTransaction.add(R.id.frame, fragment, tag);
            } else {
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
        public void onServiceConnected() {
            usersCount.setText(String.format("%d entries", getService().getDatabase().getUsersCursor().getCount()));
            size.setText(Formatter.formatFileSize(getActivity(), getActivity().getDatabasePath(CentralDatabase.DB_FILENAME).length()));
        }

        @Override
        public TwitterService getService() {
            return ((MaintenanceActivity)getActivity()).getTwitterService();
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
            ProgressDialog dialog = ProgressDialog.show(getActivity(),
                    args.getString("title"),
                    args.getString("message"),
                    args.getBoolean("indeterminate"));
            return dialog;
        }
    }
}
