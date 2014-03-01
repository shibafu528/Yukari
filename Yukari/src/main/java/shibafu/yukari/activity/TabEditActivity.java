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
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import shibafu.yukari.R;
import shibafu.yukari.common.TabInfo;
import shibafu.yukari.common.TabType;
import shibafu.yukari.fragment.SimpleAlertDialogFragment;
import shibafu.yukari.service.TwitterService;
import shibafu.yukari.service.TwitterServiceDelegate;
import shibafu.yukari.twitter.AuthUserRecord;

/**
 * Created by shibafu on 14/02/28.
 */
public class TabEditActivity extends ActionBarActivity
        implements TwitterServiceDelegate, DialogInterface.OnClickListener{

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

    @Override
    public void onBackPressed() {
        SimpleAlertDialogFragment dialogFragment = SimpleAlertDialogFragment.newInstance(
                android.R.drawable.ic_dialog_info,
                "Info", "変更はアプリの再起動後に適用されます", "OK", null);
        dialogFragment.show(getSupportFragmentManager(), "dialog");
    }

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            TwitterService.TweetReceiverBinder binder = (TwitterService.TweetReceiverBinder) service;
            TabEditActivity.this.service = binder.getService();
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

    @Override
    public void onClick(DialogInterface dialog, int which) {
        finish();
    }

    public void addTab(int type) {
        if (type < 0) return;
        if (type == TabType.TABTYPE_HOME || type == TabType.TABTYPE_MENTION || type == TabType.TABTYPE_DM) {
            findInnerFragment().addTab(type, null, null);
        }
        else {
            Intent intent = new Intent(this, AccountChooserActivity.class);
            startActivityForResult(intent, type);
        }
    }

    public void addTab(int type, AuthUserRecord userRecord) {
        if (type == TabType.TABTYPE_LIST) {
            //TODO: List選択ダイアログ

        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            AuthUserRecord userRecord = (AuthUserRecord) data.getSerializableExtra(AccountChooserActivity.EXTRA_SELECTED_RECORD);
            addTab(requestCode, userRecord);
        }
    }

    public InnerFragment findInnerFragment() {
        return ((InnerFragment)getSupportFragmentManager().findFragmentByTag(FRAGMENT_TAG));
    }

    public static class InnerFragment extends ListFragment implements DialogInterface.OnClickListener {

        private ArrayList<TabInfo> tabs;
        private TabInfo deleteReserve = null;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setHasOptionsMenu(true);
        }

        @Override
        public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
            MenuItem addMenu = menu.add(Menu.NONE, R.id.action_add, Menu.NONE, "タブの追加").setIcon(R.drawable.ic_action_add);
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

        @Override
        public void onListItemClick(ListView l, View v, int position, long id) {
            deleteReserve = tabs.get(position);
            SimpleAlertDialogFragment dialogFragment = SimpleAlertDialogFragment.newInstance(
                    android.R.drawable.ic_dialog_alert,
                    "確認", "タブを削除しますか?", "OK", "キャンセル"
            );
            dialogFragment.setTargetFragment(this, 1);
            dialogFragment.show(getChildFragmentManager(), "alert");
        }

        public void addTab(int type, AuthUserRecord userRecord, Object argument) {
            switch (type) {
                case TabType.TABTYPE_HOME:
                case TabType.TABTYPE_MENTION:
                case TabType.TABTYPE_DM:
                    for (TabInfo info : tabs) {
                        if (info.getType() == type) {
                            return;
                        }
                    }
                    ((TabEditActivity)getActivity()).getTwitterService().getDatabase()
                            .updateTab(new TabInfo(type, tabs.size() + 1, null));
                    reloadList();
                    break;
                case TabType.TABTYPE_LIST:
                    ((TabEditActivity)getActivity()).getTwitterService().getDatabase()
                            .updateTab(new TabInfo(type, tabs.size() + 1, userRecord, (Long) argument));
                    reloadList();
                    break;
            }
        }

        public void reloadList() {
            tabs = ((TabEditActivity) getActivity()).getTwitterService().getDatabase().getTabs();
            setListAdapter(new Adapter(getActivity(), tabs));
        }

        @Override
        public void onClick(DialogInterface dialog, int which) {
            if (which == DialogInterface.BUTTON_POSITIVE && deleteReserve != null) {
                ((TabEditActivity)getActivity()).getTwitterService().getDatabase()
                        .deleteTab(deleteReserve.getId());
                reloadList();
                Toast.makeText(getActivity(), "タブを削除しました", Toast.LENGTH_LONG).show();
            }
            deleteReserve = null;
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
                    .setTitle("タブの種類を選択")
                    .setItems(items, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            int type;
                            switch (which) {
                                case 0:
                                    type = TabType.TABTYPE_HOME;
                                    break;
                                case 1:
                                    type = TabType.TABTYPE_MENTION;
                                    break;
                                case 2:
                                    type = TabType.TABTYPE_DM;
                                    break;
                                case 3:
                                    type = TabType.TABTYPE_LIST;
                                    break;
                                default:
                                    type = -1;
                                    break;
                            }
                            ((TabEditActivity)getActivity()).addTab(type);
                        }
                    });
            return builder.create();
        }
    }
}