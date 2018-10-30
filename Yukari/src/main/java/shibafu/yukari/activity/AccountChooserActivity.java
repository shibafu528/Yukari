package shibafu.yukari.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import shibafu.yukari.R;
import shibafu.yukari.activity.base.ListYukariBase;
import shibafu.yukari.common.bitmapcache.ImageLoaderTask;
import shibafu.yukari.linkage.ProviderApi;
import shibafu.yukari.twitter.AuthUserRecord;
import shibafu.yukari.util.ThemeUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Shibafu on 13/12/16.
 */
public class AccountChooserActivity extends ListYukariBase {

    //初期選択のユーザを指定する
    public static final String EXTRA_SELECTED_USERID = "selected_id";
    //初期選択のユーザを複数指定する (EXTRA_SELECTED_USERIDより優先される)
    public static final String EXTRA_SELECTED_USERS  = "selected_users";
    //複数選択を許可するか設定する
    public static final String EXTRA_MULTIPLE_CHOOSE = "multiple_choose";
    //CKCSオーバライド済アカウントのみにフィルタする
    public static final String EXTRA_FILTER_CONSUMER_OVERRODE = "consumer_overrode";
    //API Typeでフィルタする
    public static final String EXTRA_FILTER_PROVIDER_API_TYPE = "provider_api_type";

    public static final String EXTRA_SELECTED_USERSN = "selected_sn";
    public static final String EXTRA_SELECTED_USERS_SN = "selected_sns";

    public static final String EXTRA_SELECTED_RECORD = "selected_record";
    public static final String EXTRA_SELECTED_RECORDS = "selected_records";

    public static final String EXTRA_METADATA = "meta";

    private boolean isMultipleChoose = false;
    private boolean isFilterConsumerOverrode = false;
    private boolean isFilterProviderApiType = false;
    private int filterProviderApiType = 0;
    private List<Long> defaultSelectedUserIds = new ArrayList<>();

    private Adapter adapter;
    private List<Data> dataList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeUtil.setDialogTheme(this);
        super.onCreate(savedInstanceState, true);
        Intent args = getIntent();

        String title = args.getStringExtra(Intent.EXTRA_TITLE);
        if (title != null) {
            setTitle(title);
        }

        isMultipleChoose = args.getBooleanExtra(EXTRA_MULTIPLE_CHOOSE, false);
        isFilterConsumerOverrode = args.getBooleanExtra(EXTRA_FILTER_CONSUMER_OVERRODE, false);
        isFilterProviderApiType = args.hasExtra(EXTRA_FILTER_PROVIDER_API_TYPE);
        filterProviderApiType = args.getIntExtra(EXTRA_FILTER_PROVIDER_API_TYPE, 0);
        setFinishOnTouchOutside(!isMultipleChoose);

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
            getListView().setOnItemLongClickListener((parent, view, position, id) -> {
                for (int i = 0; i < dataList.size(); ++i) {
                    dataList.get(i).checked = (i == position);
                }
                adapter.notifyDataSetChanged();
                return true;
            });
        }
    }

    @Override
    public void onBackPressed() {
        if (isMultipleChoose) {
            Intent result = new Intent();
            ArrayList<AuthUserRecord> userRecords = new ArrayList<>();
            for (Data d : dataList) {
                if (d.checked) {
                    userRecords.add(d.record);
                }
            }
            Log.d("AccountChooserActivity", "Return " + userRecords.size() + " account(s)");
            result.putExtra(EXTRA_SELECTED_RECORDS, userRecords);
            result.putExtra(EXTRA_METADATA, getIntent().getStringExtra(EXTRA_METADATA));
            setResult(RESULT_OK, result);
        }
        else {
            setResult(RESULT_CANCELED);
        }
        finish();
    }

    private void createList() {
        List<AuthUserRecord> users = getTwitterService().getUsers();
        dataList.clear();
        boolean isSelected;
        for (AuthUserRecord userRecord : users) {
            isSelected = defaultSelectedUserIds.contains(userRecord.NumericId);
            if (!isFilterConsumerOverrode || !userRecord.isDefaultConsumer()) {
                if (!isFilterProviderApiType || userRecord.Provider.getApiType() == filterProviderApiType) {
                    dataList.add(new Data(userRecord, isSelected));
                }
            }
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
            result.putExtra(EXTRA_METADATA, getIntent().getStringExtra(EXTRA_METADATA));
            setResult(RESULT_OK, result);
            finish();
        }
    }

    @Override
    public void onServiceConnected() {
        List<AuthUserRecord> users = getTwitterService().getUsers();
        if (isFilterConsumerOverrode && !AuthUserRecord.canUseDissonanceFunctions(users)) {
            setResult(RESULT_CANCELED);
            finish();
        } else if (!isMultipleChoose && users.size() == 1) {
            AuthUserRecord user = users.get(0);
            Intent result = new Intent();
            result.putExtra(EXTRA_SELECTED_USERID, user.NumericId);
            result.putExtra(EXTRA_SELECTED_USERSN, user.ScreenName);
            result.putExtra(EXTRA_SELECTED_RECORD, user);
            result.putExtra(EXTRA_METADATA, getIntent().getStringExtra(EXTRA_METADATA));
            setResult(RESULT_OK, result);
            finish();
        } else {
            createList();
        }
    }

    @Override
    public void onServiceDisconnected() {

    }

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
                ImageLoaderTask.loadProfileIcon(getApplicationContext(), vh.ivIcon, d.imageURL);
                vh.checkBox.setTag(position);
                vh.checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    int position1 = (Integer)buttonView.getTag();
                    getItem(position1).checked = isChecked;
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
