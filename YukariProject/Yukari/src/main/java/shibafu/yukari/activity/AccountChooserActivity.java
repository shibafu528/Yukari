package shibafu.yukari.activity;

import android.annotation.TargetApi;
import android.app.ListActivity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import shibafu.yukari.R;
import shibafu.yukari.common.IconLoaderTask;
import shibafu.yukari.service.TwitterService;
import shibafu.yukari.twitter.AuthUserRecord;

/**
 * Created by Shibafu on 13/12/16.
 */
public class AccountChooserActivity extends ListActivity {

    //初期選択のユーザを指定する
    public static final String EXTRA_SELECTED_USERID = "selected_id";
    //初期選択のユーザを複数指定する (EXTRA_SELECTED_USERIDより優先される)
    public static final String EXTRA_SELECTED_USERS  = "selected_users";
    //複数選択を許可するか設定する
    public static final String EXTRA_MULTIPLE_CHOOSE = "multiple_choose";

    public static final String EXTRA_SELECTED_USERSN = "selected_sn";
    public static final String EXTRA_SELECTED_USERS_SN = "selected_sns";

    public static final String EXTRA_SELECTED_RECORD = "selected_record";
    public static final String EXTRA_SELECTED_RECORDS = "selected_records";

    private TwitterService service;
    private boolean serviceBound = false;

    private boolean isMultipleChoose = false;
    private List<Long> defaultSelectedUserIds = new ArrayList<Long>();

    private Adapter adapter;
    private List<Data> dataList = new ArrayList<Data>();

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent args = getIntent();

        String title = args.getStringExtra(Intent.EXTRA_TITLE);
        if (title != null) {
            setTitle(title);
        }

        isMultipleChoose = args.getBooleanExtra(EXTRA_MULTIPLE_CHOOSE, false);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            setFinishOnTouchOutside(!isMultipleChoose);
        }

        long[] defaultSelected = args.getLongArrayExtra(EXTRA_SELECTED_USERS);
        ArrayList<AuthUserRecord> selectedUsers = (ArrayList<AuthUserRecord>) args.getSerializableExtra(EXTRA_SELECTED_RECORDS);
        if (defaultSelected == null) {
            if (selectedUsers != null) for (AuthUserRecord userRecord : selectedUsers) {
                defaultSelectedUserIds.add(userRecord.NumericId);
            }
            else {
                long defaultSelectedId = args.getLongExtra(EXTRA_SELECTED_USERID, -1);
                if (defaultSelectedId > -1) {
                    defaultSelectedUserIds.add(defaultSelectedId);
                }
            }
        }
        else for (long id : defaultSelected) {
            defaultSelectedUserIds.add(id);
        }

        if (isMultipleChoose) {
            getListView().setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
                @Override
                public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                    for (int i = 0; i < dataList.size(); ++i) {
                        dataList.get(i).checked = (i == position);
                    }
                    adapter.notifyDataSetChanged();
                    return true;
                }
            });
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        bindService(new Intent(this, TwitterService.class), connection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unbindService(connection);
    }

    @Override
    public void onBackPressed() {
        if (isMultipleChoose) {
            Intent result = new Intent();
            ArrayList<AuthUserRecord> userRecords = new ArrayList<AuthUserRecord>();
            for (Data d : dataList) {
                if (d.checked) {
                    userRecords.add(d.record);
                }
            }
            Log.d("AccountChooserActivity", "Return " + userRecords.size() + " account(s)");
            result.putExtra(EXTRA_SELECTED_RECORDS, userRecords);
            setResult(RESULT_OK, result);
        }
        else {
            setResult(RESULT_CANCELED);
        }
        finish();
    }

    private void createList() {
        List<AuthUserRecord> users = service.getUsers();
        dataList.clear();
        boolean isSelected;
        for (AuthUserRecord userRecord : users) {
            isSelected = defaultSelectedUserIds.contains(userRecord.NumericId);
            dataList.add(new Data(userRecord, isSelected));
        }
        adapter = new Adapter(AccountChooserActivity.this, dataList);
        setListAdapter(adapter);
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);

        if (isMultipleChoose) {
            CheckBox cb = ((Adapter.ViewHolder)v.getTag()).checkBox;
            cb.setChecked(!cb.isChecked());
        }
        else {
            Data user = dataList.get(position);
            Intent result = new Intent();
            result.putExtra(EXTRA_SELECTED_USERID, user.id);
            result.putExtra(EXTRA_SELECTED_USERSN, user.sn);
            result.putExtra(EXTRA_SELECTED_RECORD, user.record);
            setResult(RESULT_OK, result);
            finish();
        }
    }

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            TwitterService.TweetReceiverBinder binder = (TwitterService.TweetReceiverBinder) service;
            AccountChooserActivity.this.service = binder.getService();
            createList();
            serviceBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
        }
    };

    private class Data {
        long id;
        String name;
        String sn;
        String imageURL;
        boolean checked;
        AuthUserRecord record;

        private Data(AuthUserRecord record, boolean checked) {
            this.record = record;
            this.id = record.NumericId;
            this.name = record.Name;
            this.sn = record.ScreenName;
            this.imageURL = record.ProfileImageUrl;
            this.checked = checked;
        }
    }

    private class Adapter extends ArrayAdapter<Data> {

        private LayoutInflater inflater;

        public Adapter(Context context, List<Data> objects) {
            super(context, 0, objects);
            inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            ViewHolder vh;

            if (v == null) {
                v = inflater.inflate(R.layout.row_account, parent, false);
                vh = new ViewHolder();
                vh.ivIcon = (ImageView) v.findViewById(R.id.user_icon);
                vh.tvName = (TextView) v.findViewById(R.id.user_name);
                vh.tvScreenName = (TextView) v.findViewById(R.id.user_sn);
                vh.checkBox = (CheckBox) v.findViewById(R.id.user_check);
                if (!isMultipleChoose) {
                    vh.checkBox.setVisibility(View.GONE);
                }
                v.setTag(vh);
            }
            else {
                vh = (ViewHolder) v.getTag();
            }

            Data d = getItem(position);
            if (d != null) {
                vh.tvName.setText(d.name);
                vh.tvScreenName.setText("@" + d.sn);
                vh.ivIcon.setImageResource(R.drawable.yukatterload);
                vh.ivIcon.setTag(d.imageURL);
                IconLoaderTask task = new IconLoaderTask(AccountChooserActivity.this, vh.ivIcon);
                task.executeIf(d.imageURL);
                vh.checkBox.setTag(position);
                vh.checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        int position = (Integer)buttonView.getTag();
                        getItem(position).checked = isChecked;
                    }
                });
                vh.checkBox.setChecked(d.checked);
            }

            return v;
        }

        public class ViewHolder {
            ImageView ivIcon;
            TextView tvScreenName;
            TextView tvName;
            CheckBox checkBox;
        }
    }
}
