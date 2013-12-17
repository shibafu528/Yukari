package shibafu.yukari.activity;

import android.app.ListActivity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.loopj.android.image.SmartImageView;

import java.util.ArrayList;
import java.util.List;

import shibafu.yukari.R;
import shibafu.yukari.common.IconLoaderTask;
import shibafu.yukari.common.SimpleAsyncTask;
import shibafu.yukari.service.TwitterService;
import shibafu.yukari.twitter.AuthUserRecord;
import twitter4j.TwitterException;
import twitter4j.User;

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

    private TwitterService service;
    private boolean serviceBound = false;

    private boolean isMultipleChoose = false;
    private List<Long> defaultSelectedUserIds = new ArrayList<Long>();

    private Adapter adapter;
    private List<Data> dataList;
    private AsyncTask<Void,Void,List<Data>> userLoadTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("アカウント一覧を読込中...");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            setFinishOnTouchOutside(true);
        }

        Intent args = getIntent();
        isMultipleChoose = args.getBooleanExtra(EXTRA_MULTIPLE_CHOOSE, false);
        long[] defaultSelected = args.getLongArrayExtra(EXTRA_SELECTED_USERS);
        if (defaultSelected == null) {
            long defaultSelectedId = args.getLongExtra(EXTRA_SELECTED_USERID, -1);
            if (defaultSelectedId > -1) {
                defaultSelectedUserIds.add(defaultSelectedId);
            }
        }
        else for (long id : defaultSelected) {
            defaultSelectedUserIds.add(id);
        }

        bindService(new Intent(this, TwitterService.class), connection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unbindService(connection);
        if (userLoadTask != null) {
            userLoadTask.cancel(true);
        }
    }

    @Override
    public void onBackPressed() {
        if (isMultipleChoose) {
            //TODO: 複数選択の結果を格納するかんじで
        }
        else {
            setResult(RESULT_CANCELED);
        }
        finish();
    }

    private void createList() {
        //TODO: 正直ダウンロード処理とかやるだけ無駄だと思うのでDB化の際にはこの辺の情報あらかじめ保存してくれ
        userLoadTask = new AsyncTask<Void, Void, List<Data>>() {
            @Override
            protected List<Data> doInBackground(Void... params) {
                List<AuthUserRecord> users = service.getUsers();
                List<Data> data = new ArrayList<Data>();
                boolean isSelected;
                for (AuthUserRecord userRecord : users) {
                    if (isCancelled()) break;
                    isSelected = defaultSelectedUserIds.contains(userRecord.NumericId);
                    try {
                        User user = userRecord.getUser(AccountChooserActivity.this);
                        data.add(new Data(user.getId(), user.getName(), user.getScreenName(), user.getProfileImageURLHttps(), isSelected));
                    } catch (TwitterException e) {
                        e.printStackTrace();
                        data.add(new Data(userRecord.NumericId, "", userRecord.ScreenName, null, isSelected));
                    }
                }
                return data;
            }

            @Override
            protected void onPostExecute(List<Data> datas) {
                if (datas == null) {
                    Toast.makeText(AccountChooserActivity.this, "アカウント一覧の取得に失敗しました", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_CANCELED);
                    finish();
                    return;
                }
                dataList = datas;
                adapter = new Adapter(AccountChooserActivity.this, dataList);
                setListAdapter(adapter);
                setTitle("アカウントを選択");

                userLoadTask = null;
            }
        };
        userLoadTask.execute();
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);

        if (isMultipleChoose) {

        }
        else {
            Data user = dataList.get(position);
            Intent result = new Intent();
            result.putExtra(EXTRA_SELECTED_USERID, user.id);
            result.putExtra(EXTRA_SELECTED_USERSN, user.sn);
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

        private Data(long id, String name, String sn, String imageURL, boolean checked) {
            this.id = id;
            this.name = name;
            this.sn = sn;
            this.imageURL = imageURL;
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
                vh.ivIcon = (SmartImageView) v.findViewById(R.id.user_icon);
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
                vh.checkBox.setChecked(d.checked);
            }

            return v;
        }

        private class ViewHolder {
            SmartImageView ivIcon;
            TextView tvScreenName;
            TextView tvName;
            CheckBox checkBox;
        }
    }
}
