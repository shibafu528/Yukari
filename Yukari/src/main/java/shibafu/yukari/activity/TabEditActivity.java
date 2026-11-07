package shibafu.yukari.activity;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.fragment.app.ListFragment;

import java.util.ArrayList;
import java.util.List;

import shibafu.yukari.R;
import shibafu.yukari.activity.base.ActionBarYukariBase;
import shibafu.yukari.common.TabInfo;
import shibafu.yukari.common.TabType;
import shibafu.yukari.common.async.ThrowableAsyncTask;
import shibafu.yukari.core.App;
import shibafu.yukari.database.AuthUserRecord;
import shibafu.yukari.database.CentralDatabase;
import shibafu.yukari.databinding.RowTabeditBinding;
import shibafu.yukari.filter.compiler.QueryCompiler;
import shibafu.yukari.fragment.SimpleAlertDialogFragment;
import shibafu.yukari.twitter.TwitterUtil;
import twitter4j.ResponseList;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.UserList;

/**
 * Created by shibafu on 14/02/28.
 */
public class TabEditActivity extends ActionBarYukariBase implements SimpleAlertDialogFragment.OnDialogChoseListener {

    private static final String FRAGMENT_TAG = "inner";
    private static final int DIALOG_FINISH = 0;

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
                0, "Info", "変更はアプリの再起動後に適用されます", "OK", null);
        dialogFragment.show(getSupportFragmentManager(), "dialog");
    }

    @Override
    public void onDialogChose(int requestCode, int which, @Nullable Bundle extras) {
        if (requestCode == DIALOG_FINISH) {
            finish();
        }
    }

    public void addTab(int type) {
        findInnerFragment().addTab(type, null);
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
        } else if (type == TabType.TABTYPE_FILTER && args.length == 1) {
            String template = (String) args[0];
            template = template.replace("@@", userRecord.ScreenName);
            findInnerFragment().addTab(type, userRecord, template);
        }
    }

    public void chooseAccountAndAddTab(int type, String template) {
        Intent intent = new Intent(this, AccountChooserActivity.class);
        intent.putExtra(AccountChooserActivity.EXTRA_METADATA, template);
        startActivityForResult(intent, type);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            AuthUserRecord userRecord = (AuthUserRecord) data.getSerializableExtra(AccountChooserActivity.EXTRA_SELECTED_RECORD);
            String template = data.getStringExtra(AccountChooserActivity.EXTRA_METADATA);
            if (TextUtils.isEmpty(template)) {
                addTab(requestCode, userRecord);
            } else {
                addTab(requestCode, userRecord, template);
            }
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

    public static class InnerFragment extends ListFragment implements SimpleAlertDialogFragment.OnDialogChoseListener {

        private static final int DIALOG_CONFIRM = 1;
        private static final int REQUEST_EDIT_QUERY = 1;
        private static final String EXTRA_ID = "id";

        private Adapter adapter;
        private ArrayList<TabInfo> tabs = new ArrayList<>();
        private TabInfo deleteReserve = null;

        private boolean sortable;
        private TabInfo grabbedTab;
        private int dragPosition = -1;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setHasOptionsMenu(true);
        }

        @Override
        public void onViewCreated(View view, Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            adapter = new Adapter(getActivity(), tabs);
            setListAdapter(adapter);
            getListView().setOnTouchListener((v, event) -> {
                if (!sortable) {
                    return false;
                }
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        break;
                    case MotionEvent.ACTION_MOVE: {
                        int position = getListView().pointToPosition((int) event.getX(), (int) event.getY());
                        if (position < 0) {
                            break;
                        }
                        if (position != dragPosition) {
                            dragPosition = position;
                            adapter.remove(grabbedTab);
                            adapter.insert(grabbedTab, dragPosition);
                        }
                        return true;
                    }
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                    case MotionEvent.ACTION_OUTSIDE:
                        stopDrag();
                        return true;
                }
                return false;
            });
            getListView().setOnItemLongClickListener((parent, v, position, id) -> {
                if (tabs.get(position).getType() != TabType.TABTYPE_FILTER) {
                    return false;
                }

                Intent intent = new Intent(getActivity().getApplicationContext(), QueryEditorActivity.class)
                        .putExtra(EXTRA_ID, tabs.get(position).getId())
                        .putExtra(Intent.EXTRA_TEXT, tabs.get(position).getFilterQuery());
                startActivityForResult(intent, REQUEST_EDIT_QUERY);
                return true;
            });
        }

        @Override
        public void onPause() {
            super.onPause();
            syncDatabase();
        }

        @Override
        public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
            MenuItem addMenu = menu.add(Menu.NONE, R.id.action_add, Menu.NONE, "タブの追加").setIcon(R.drawable.ic_action_add);
            addMenu.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            switch (item.getItemId()) {
                case R.id.action_add:
                {
                    TypeChooseDialogFragment dialogFragment = new TypeChooseDialogFragment();
                    dialogFragment.show(getFragmentManager(), "typechoose");
                    return true;
                }
            }
            return super.onOptionsItemSelected(item);
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            if (requestCode == REQUEST_EDIT_QUERY && resultCode == RESULT_OK) {
                long id = data.getLongExtra(EXTRA_ID, -1);
                String newQuery = data.getStringExtra(Intent.EXTRA_TEXT);
                assert newQuery != null : "newQuery is null";

                for (TabInfo tab : tabs) {
                    if (tab.getId() == id) {
                        tab.setFilterQuery(newQuery);

                        CentralDatabase database = App.getInstance(requireContext()).getDatabase();
                        database.updateRecord(tab);
                        break;
                    }
                }

                reloadList();
            }
        }

        @Override
        public void onListItemClick(ListView l, View v, int position, long id) {
            deleteReserve = tabs.get(position);
            SimpleAlertDialogFragment dialogFragment = SimpleAlertDialogFragment.newInstance(
                    DIALOG_CONFIRM, "確認", "タブを削除しますか?", "OK", "キャンセル"
            );
            dialogFragment.setTargetFragment(this, DIALOG_CONFIRM);
            dialogFragment.show(getFragmentManager(), "alert");
        }

        public void addTab(int type, AuthUserRecord userRecord, Object... args) {
            CentralDatabase database = App.getInstance(requireContext()).getDatabase();
            switch (type) {
                case TabType.TABTYPE_HOME:
                case TabType.TABTYPE_MENTION:
                case TabType.TABTYPE_DM:
                case TabType.TABTYPE_NOTIFICATION:
                    for (TabInfo info : tabs) {
                        if (info.getType() == type && info.getBindAccount() == userRecord) {
                            return;
                        }
                    }
                    database.updateRecord(new TabInfo(type, tabs.size() + 1, userRecord));
                    reloadList();
                    break;
                case TabType.TABTYPE_FILTER: {
                    TabInfo info = new TabInfo(
                            type,
                            tabs.size() + 1,
                            null,
                            QueryCompiler.DEFAULT_QUERY);
                    if (args.length == 1) {
                        info.setFilterQuery((String) args[0]);
                    }
                    database.updateRecord(info);
                    reloadList();
                    break;
                }
                case TabType.TABTYPE_LIST:
                    database.updateRecord(new TabInfo(
                            type,
                            tabs.size() + 1,
                            userRecord,
                            (Long) args[0],
                            (String) args[1]));
                    reloadList();
                    break;
            }
        }

        public void syncDatabase() {
            for (int i = 0; i < tabs.size(); i++) {
                tabs.get(i).setOrder(i);
            }
            App.getInstance(requireContext()).getDatabase().updateRecord(tabs);
        }

        public void reloadList() {
            //全て再読み込み
            tabs.clear();
            tabs.addAll(App.getInstance(requireContext()).getDatabase().getTabs());
            adapter.notifyDataSetChanged();
        }

        @Override
        public void onDialogChose(int requestCode, int which, @Nullable Bundle extras) {
            if (requestCode == DIALOG_CONFIRM) {
                if (which == DialogInterface.BUTTON_POSITIVE && deleteReserve != null) {
                    App.getInstance(requireContext()).getDatabase().deleteRecord(deleteReserve);
                    reloadList();
                    Toast.makeText(getActivity(), "タブを削除しました", Toast.LENGTH_LONG).show();
                }
                deleteReserve = null;
            }
        }

        private void startDrag(TabInfo tabInfo) {
            dragPosition = -1;
            sortable = true;
            grabbedTab = tabInfo;
            adapter.notifyDataSetChanged();
        }

        private void stopDrag() {
            dragPosition = -1;
            sortable = false;
            grabbedTab = null;
            adapter.notifyDataSetChanged();
            syncDatabase();
        }

        private class Adapter extends ArrayAdapter<TabInfo> {
            private LayoutInflater inflater;

            public Adapter(Context context, List<TabInfo> objects) {
                super(context, 0, objects);
                inflater = (LayoutInflater) context.getSystemService(LAYOUT_INFLATER_SERVICE);
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                RowTabeditBinding viewHolder;
                if (convertView == null) {
                    viewHolder = RowTabeditBinding.inflate(inflater);
                    convertView = viewHolder.getRoot();
                    convertView.setTag(viewHolder);
                } else {
                    viewHolder = (RowTabeditBinding) convertView.getTag();
                }
                final TabInfo item = getItem(position);
                if (item != null) {
                    viewHolder.text1.setText(item.getTitle());
                    viewHolder.handle.setOnTouchListener((v, event) -> {
                        if (event.getAction() == MotionEvent.ACTION_DOWN) {
                            startDrag(item);
                            return true;
                        }
                        return false;
                    });
                    viewHolder.radio.setOnCheckedChangeListener(null);
                    viewHolder.radio.setChecked(item.isStartup());
                    viewHolder.radio.setOnCheckedChangeListener((buttonView, isChecked) -> {
                        if (isChecked) {
                            for (TabInfo tab : tabs) {
                                tab.setStartup(tab.getId() == item.getId());
                            }
                            syncDatabase();
                            notifyDataSetChanged();
                        }
                    });

                    if (grabbedTab != null && grabbedTab.getId() == item.getId()) {
                        convertView.setBackgroundColor(Color.parseColor("#9933b5e5"));
                    } else {
                        convertView.setBackgroundColor(Color.TRANSPARENT);
                    }
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
                    "Notifications",
                    "Notifications (Single Account)",
                    "Filter",
                    "Mastodon ローカルTL",
                    "Mastodon 連合TL"
            };
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                    .setTitle("タブの種類を選択")
                    .setItems(items, (dialog, which) -> {
                        boolean requireBind = false;
                        int type;
                        String template = "";
                        switch (which) {
                            case 0:
                                type = TabType.TABTYPE_HOME;
                                break;
                            case 1:
                                type = TabType.TABTYPE_HOME;
                                requireBind = true;
                                break;
                            case 2:
                                type = TabType.TABTYPE_MENTION;
                                break;
                            case 3:
                                type = TabType.TABTYPE_MENTION;
                                requireBind = true;
                                break;
                            case 4:
                                type = TabType.TABTYPE_DM;
                                break;
                            case 5:
                                type = TabType.TABTYPE_DM;
                                requireBind = true;
                                break;
                            case 6:
                                type = TabType.TABTYPE_LIST;
                                requireBind = true;
                                break;
                            case 7:
                                type = TabType.TABTYPE_NOTIFICATION;
                                break;
                            case 8:
                                type = TabType.TABTYPE_NOTIFICATION;
                                requireBind = true;
                                break;
                            case 9:
                                type = TabType.TABTYPE_FILTER;
                                break;
                            case 10:
                                type = TabType.TABTYPE_FILTER;
                                requireBind = true;
                                template = "from don_local:\"@@\"";
                                break;
                            case 11:
                                type = TabType.TABTYPE_FILTER;
                                requireBind = true;
                                template = "from don_federated:\"@@\"";
                                break;
                            default:
                                throw new RuntimeException();
                        }
                        if (requireBind) {
                            ((TabEditActivity)getActivity()).chooseAccountAndAddTab(type, template);
                        } else {
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
                    .setAdapter(new Adapter(getActivity(), userLists), (dialog1, which) -> {
                        Bundle args = getArguments();
                        UserList userList = userLists.get(which);
                        ((TabEditActivity) getActivity()).addTab(
                                TabType.TABTYPE_LIST,
                                (AuthUserRecord) args.getSerializable(ARG_USER),
                                userList.getId(),
                                userList.getName());
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
                                try {
                                    Twitter twitter = TwitterUtil.getTwitterOrThrow(requireContext(), params[0]);
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
                    if (item.getUser().getId() == ((AuthUserRecord) getArguments().getSerializable(ARG_USER)).NumericId) {
                        tv.setText(item.getName());
                    } else {
                        tv.setText(String.format("@%s/%s", item.getUser().getScreenName(), item.getName()));
                    }
                }
                return convertView;
            }
        }
    }
}