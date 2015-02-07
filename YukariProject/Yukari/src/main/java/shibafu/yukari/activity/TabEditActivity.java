package shibafu.yukari.activity;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.ListFragment;
import android.support.v4.view.MenuItemCompat;
import android.view.LayoutInflater;
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
import shibafu.yukari.activity.base.ActionBarYukariBase;
import shibafu.yukari.common.TabInfo;
import shibafu.yukari.common.TabType;
import shibafu.yukari.common.async.ThrowableAsyncTask;
import shibafu.yukari.fragment.SimpleAlertDialogFragment;
import shibafu.yukari.twitter.AuthUserRecord;
import twitter4j.ResponseList;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.UserList;

/**
 * Created by shibafu on 14/02/28.
 */
public class TabEditActivity extends ActionBarYukariBase implements DialogInterface.OnClickListener{

    private static final String FRAGMENT_TAG = "inner";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_parent);
        if (savedInstanceState == null) {
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            InnerFragment fragment = new InnerFragment();
            transaction.replace(R.id.frame, fragment, FRAGMENT_TAG);
            transaction.commit();
        }
    }

    @Override
    public void onBackPressed() {
        SimpleAlertDialogFragment dialogFragment = SimpleAlertDialogFragment.newInstance(
                "Info", "変更はアプリの再起動後に適用されます", "OK", null);
        dialogFragment.show(getSupportFragmentManager(), "dialog");
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        finish();
    }

    public void addTab(int type) {
        if (type < 0) return;
        boolean requireBind = (type & 0x80) == 0x80;
        type &= 0x7f;
        if (!requireBind && (type == TabType.TABTYPE_HOME || type == TabType.TABTYPE_MENTION || type == TabType.TABTYPE_DM || type == TabType.TABTYPE_HISTORY)) {
            findInnerFragment().addTab(type, null);
        }
        else {
            Intent intent = new Intent(this, AccountChooserActivity.class);
            startActivityForResult(intent, type);
        }
    }

    public void addTab(int type, AuthUserRecord userRecord) {
        if (type == TabType.TABTYPE_LIST) {
            ListChooseDialogFragment dialogFragment = ListChooseDialogFragment.newInstance(userRecord);
            dialogFragment.show(getSupportFragmentManager(), "list");
        } else {
            findInnerFragment().addTab(type, userRecord);
        }
    }

    public void addTab(int type, AuthUserRecord userRecord, Object... args) {
        if (type == TabType.TABTYPE_LIST && args.length == 2) {
            findInnerFragment().addTab(type, userRecord, args);
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

    @Override
    public void onServiceConnected() {
        findInnerFragment().reloadList();
    }

    @Override
    public void onServiceDisconnected() {

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
                    "確認", "タブを削除しますか?", "OK", "キャンセル"
            );
            dialogFragment.setTargetFragment(this, 1);
            dialogFragment.show(getChildFragmentManager(), "alert");
        }

        public void addTab(int type, AuthUserRecord userRecord, Object... args) {
            switch (type) {
                case TabType.TABTYPE_HOME:
                case TabType.TABTYPE_MENTION:
                case TabType.TABTYPE_DM:
                case TabType.TABTYPE_HISTORY:
                    for (TabInfo info : tabs) {
                        if (info.getType() == type && info.getBindAccount() == userRecord) {
                            return;
                        }
                    }
                    ((TabEditActivity)getActivity()).getTwitterService().getDatabase()
                            .updateRecord(new TabInfo(type, tabs.size() + 1, userRecord));
                    reloadList();
                    break;
                case TabType.TABTYPE_LIST:
                    ((TabEditActivity)getActivity()).getTwitterService().getDatabase()
                            .updateRecord(new TabInfo(
                                    type,
                                    tabs.size() + 1,
                                    userRecord,
                                    (Long) args[0],
                                    (String) args[1]));
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
                        .deleteRecord(deleteReserve);
                reloadList();
                Toast.makeText(getActivity(), "タブを削除しました", Toast.LENGTH_LONG).show();
            }
            deleteReserve = null;
        }

        private class Adapter extends ArrayAdapter<TabInfo> {
            private LayoutInflater inflater;

            public Adapter(Context context, List<TabInfo> objects) {
                super(context, 0, objects);
                inflater = (LayoutInflater) context.getSystemService(LAYOUT_INFLATER_SERVICE);
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    convertView = inflater.inflate(android.R.layout.simple_list_item_1, null);
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
                    "Home (Single Account)",
                    "Mentions",
                    "Mentions (Single Account)",
                    "DM",
                    "DM (Single Account)",
                    "List",
                    "History"
            };
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                    .setTitle("タブの種類を選択")
                    .setItems(items, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            int type = 0;
                            if (which % 2 == 1) {
                                type = 0x80;
                            }
                            switch (which) {
                                case 0:
                                case 1:
                                    type |= TabType.TABTYPE_HOME;
                                    break;
                                case 2:
                                case 3:
                                    type |= TabType.TABTYPE_MENTION;
                                    break;
                                case 4:
                                case 5:
                                    type |= TabType.TABTYPE_DM;
                                    break;
                                case 6:
                                    type |= TabType.TABTYPE_LIST;
                                    break;
                                case 7:
                                    type = TabType.TABTYPE_HISTORY;
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

    public static class ListChooseDialogFragment extends DialogFragment {
        private List<UserList> userLists = new ArrayList<>();
        private AlertDialog dialog;
        public static final String ARG_USER = "user";

        public static ListChooseDialogFragment newInstance(AuthUserRecord userRecord) {
            ListChooseDialogFragment fragment = new ListChooseDialogFragment();
            Bundle args = new Bundle();
            args.putSerializable(ARG_USER, userRecord);
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            dialog = new AlertDialog.Builder(getActivity())
                    .setTitle("Loading...")
                    .setAdapter(new Adapter(getActivity(), userLists), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Bundle args = getArguments();
                            UserList userList = userLists.get(which);
                            ((TabEditActivity) getActivity()).addTab(
                                    TabType.TABTYPE_LIST,
                                    (AuthUserRecord) args.getSerializable(ARG_USER),
                                    userList.getId(),
                                    userList.getSlug());
                        }
                    })
                    .create();
            Bundle args = getArguments();
            loadLists((AuthUserRecord) args.getSerializable(ARG_USER));
            return dialog;
        }

        private void loadLists(AuthUserRecord userRecord) {
            if (userLists.isEmpty() && userRecord != null) {
                ThrowableAsyncTask<AuthUserRecord, Void, ResponseList<UserList>> task =
                        new ThrowableAsyncTask<AuthUserRecord, Void, ResponseList<UserList>>() {
                            @Override
                            protected ThrowableResult<ResponseList<UserList>> doInBackground(AuthUserRecord... params) {
                                Twitter twitter = ((TabEditActivity) getActivity()).getTwitterService().getTwitter();
                                twitter.setOAuthAccessToken(params[0].getAccessToken());
                                try {
                                    return new ThrowableResult<>(twitter.getUserLists(params[0].NumericId));
                                } catch (TwitterException e) {
                                    e.printStackTrace();
                                    return new ThrowableResult<>(e);
                                }
                            }

                            @Override
                            protected void onPostExecute(ThrowableResult<ResponseList<UserList>> result) {
                                if (result != null && !result.isException()) {
                                    userLists = result.getResult();
                                    loadLists(null);
                                }
                                else if (result != null) {
                                    Exception e = result.getException();
                                    if (e instanceof TwitterException) {
                                        TwitterException te = (TwitterException) e;
                                        Toast.makeText(getActivity(),
                                                String.format("Listの取得中にエラー: %d\n%s",
                                                        te.getErrorCode(),
                                                        te.getErrorMessage()),
                                                Toast.LENGTH_LONG).show();
                                    }
                                    else {
                                        Toast.makeText(getActivity(),
                                                String.format("Listの取得中にエラー: \n%s",
                                                        e.getMessage()),
                                                Toast.LENGTH_LONG).show();
                                    }
                                    dismiss();
                                }
                                else {
                                    dismiss();
                                    Toast.makeText(getActivity(),
                                            "Listの取得中にエラー",
                                            Toast.LENGTH_LONG).show();
                                }
                            }
                        };
                task.execute(userRecord);
            }
            else {
                dialog.setTitle("Listを選択");
                dialog.getListView().setAdapter(new Adapter(getActivity(), userLists));
            }
        }

        private class Adapter extends ArrayAdapter<UserList> {

            public Adapter(Context context, List<UserList> objects) {
                super(context, 0, objects);
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    convertView = getActivity().getLayoutInflater().inflate(
                            android.R.layout.simple_list_item_1, null);
                }
                UserList item = getItem(position);
                if (item != null) {
                    TextView tv = (TextView) convertView.findViewById(android.R.id.text1);
                    tv.setText(item.getFullName());
                }
                return convertView;
            }
        }
    }
}