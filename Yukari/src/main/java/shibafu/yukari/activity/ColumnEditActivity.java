package shibafu.yukari.activity;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.ListFragment;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import shibafu.yukari.R;
import shibafu.yukari.common.TabInfo;
import shibafu.yukari.service.TwitterService;
import shibafu.yukari.service.TwitterServiceDelegate;

/**
 * Created by shibafu on 14/02/28.
 */
public class ColumnEditActivity extends ActionBarActivity implements TwitterServiceDelegate{

    private static final String FRAGMENT_TAG = "inner";
    private TwitterService service;
    private boolean serviceBound = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            InnerFragment fragment = new InnerFragment();
            transaction.replace(android.R.id.content, fragment, FRAGMENT_TAG);
            transaction.commit();
        }
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
            ColumnEditActivity.this.service = binder.getService();
            serviceBound = true;

            InnerFragment fragment = (InnerFragment) getSupportFragmentManager().findFragmentByTag(FRAGMENT_TAG);
            fragment.reloadList();
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

    public static class InnerFragment extends ListFragment {

        private ArrayList<TabInfo> tabs;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setHasOptionsMenu(true);
        }

        @Override
        public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
            MenuItem addMenu = menu.add(Menu.NONE, R.id.action_add, Menu.NONE, "カラムの追加").setIcon(R.drawable.ic_action_add);
            MenuItemCompat.setShowAsAction(addMenu, MenuItemCompat.SHOW_AS_ACTION_IF_ROOM);
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            switch (item.getItemId()) {
                case R.id.action_add:
                {
                    TypeChooseDialogFragment dialogFragment = new TypeChooseDialogFragment();
                    dialogFragment.show(getChildFragmentManager(), "typechoose");
                    return true;
                }
            }
            return super.onOptionsItemSelected(item);
        }

        public void reloadList() {
            tabs = ((ColumnEditActivity) getActivity()).getTwitterService().getDatabase().getTabs();
            setListAdapter(new Adapter(getActivity(), tabs));
        }

        private class Adapter extends ArrayAdapter<TabInfo> {

            public Adapter(Context context, List<TabInfo> objects) {
                super(context, 0, objects);
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    convertView = getActivity().getLayoutInflater().inflate(android.R.layout.simple_list_item_1, null);
                }
                TabInfo item = getItem(position);
                if (item != null) {
                    TextView tv = (TextView) convertView.findViewById(android.R.id.text1);
                    tv.setText(item.getTitle());
                }
                return convertView;
            }
        }
    }

    public static class TypeChooseDialogFragment extends DialogFragment {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            String[] items = {
                    "Home",
                    "Mentions",
                    "DM",
                    "List"
            };
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                    .setTitle("カラムの種類を選択")
                    .setItems(items, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {

                        }
                    });
            return builder.create();
        }
    }
}