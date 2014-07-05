package shibafu.yukari.activity;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import butterknife.ButterKnife;
import butterknife.InjectView;
import butterknife.OnClick;
import shibafu.yukari.R;
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
    }

    public static class DBMaintenanceFragment extends Fragment implements ServiceConnectable{

        @InjectView(R.id.tvYdbName) TextView title;
        @InjectView(R.id.tvYdbUserEnt) TextView usersCount;
        @InjectView(R.id.btnYdbWipe) Button usersWipeButton;
        @InjectView(R.id.btnYdbVaccum) Button vaccumButton;

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View v = inflater.inflate(R.layout.fragment_dbmt, container, false);
            ButterKnife.inject(this, v);

            title.setText(String.format("%s, version %d", CentralDatabase.DB_FILENAME, CentralDatabase.DB_VER));
            return v;
        }

        @Override
        public void onDestroyView() {
            super.onDestroyView();
            ButterKnife.reset(this);
        }

        @OnClick(R.id.btnYdbWipe)
        void onClickWipe(Button button) {

        }

        @OnClick(R.id.btnYdbVaccum)
        void onClickVaccum(Button button) {

        }

        @Override
        public void onServiceConnected() {
            usersCount.setText(String.format("%d entries", ((TwitterServiceDelegate)getActivity()).getTwitterService().getDatabase().getUsersCursor().getCount()));
        }
    }
}
