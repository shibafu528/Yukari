package shibafu.yukari.activity;

import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.os.Bundle;
import android.app.Activity;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import com.loopj.android.image.SmartImageView;

import java.util.ArrayList;
import java.util.List;

import shibafu.yukari.R;
import shibafu.yukari.database.FriendsDBHelper;
import shibafu.yukari.service.FriendsDBUpdateService;
import twitter4j.User;

public class SNPickerActivity extends Activity {

    public static final String EXTRA_USER_ID = "user_id";
    public static final String EXTRA_SCREEN_NAME = "screen_name";
    public static final String EXTRA_NAME = "name";

    private ProgressDialog currentProgress;
    private ListView listView;
    private TextView tvHead;
    private EditText editText;

    private BroadcastReceiver updatedListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (currentProgress != null) {
                currentProgress.dismiss();
                currentProgress = null;
                updateList();
            }
        }
    };
    private Adapter adapter;
    private ArrayList<Data> dataList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_snpicker);

        Button btnReload = (Button) findViewById(R.id.btnSNPickReload);
        btnReload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startService(new Intent(SNPickerActivity.this, FriendsDBUpdateService.class));
            }
        });

        tvHead = (TextView) findViewById(R.id.tvSNPickHead);
        listView = (ListView) findViewById(R.id.lvSNPick);
        dataList = new ArrayList<Data>();
        adapter = new Adapter(this, dataList);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Data d = dataList.get(position);
                Intent intent = new Intent();
                intent.putExtra(EXTRA_USER_ID, d.id);
                intent.putExtra(EXTRA_NAME, d.name);
                intent.putExtra(EXTRA_SCREEN_NAME, d.sn);
                setResult(RESULT_OK, intent);
                finish();
            }
        });
        editText = (EditText) findViewById(R.id.etSNPickName);
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                updateList();
            }
        });

        updateList();
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(updatedListener, new IntentFilter(FriendsDBUpdateService.NOTICE_UPDATED));
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(updatedListener);
    }

    private void updateList() {
        FriendsDBHelper db = new FriendsDBHelper(this);
        db.open();

        String et = editText.getText().toString();
        Cursor c;
        if (et.equals("")) {
            c = db.getAllFriends();
        }
        else {
            et = "%" + et + "%";
            c = db.getFriends(FriendsDBHelper.COL_NAME + " LIKE ? OR " + FriendsDBHelper.COL_SCREEN_NAME + " LIKE ?",
                    new String[] {et, et});
        }

        final int rowId = c.getColumnIndex(FriendsDBHelper.COL_USER_ID);
        final int rowName = c.getColumnIndex(FriendsDBHelper.COL_NAME);
        final int rowSN = c.getColumnIndex(FriendsDBHelper.COL_SCREEN_NAME);
        final int rowURL = c.getColumnIndex(FriendsDBHelper.COL_PICTURE_URL);

        dataList.clear();

        if (c.moveToFirst()) {
            do {
                Data d = new Data(c.getLong(rowId), c.getString(rowName), c.getString(rowSN), c.getString(rowURL));
                dataList.add(d);
            } while (c.moveToNext());
        }
        tvHead.setText("Suggest (" + dataList.size() + ")");
        adapter.notifyDataSetChanged();
        c.close();
        db.close();
    }

    private class Data {
        long id;
        String name;
        String sn;
        String imageURL;

        private Data(long id, String name, String sn, String imageURL) {
            this.id = id;
            this.name = name;
            this.sn = sn;
            this.imageURL = imageURL;
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
                v = inflater.inflate(R.layout.row_user, parent, false);
                vh = new ViewHolder();
                vh.ivIcon = (SmartImageView) v.findViewById(R.id.user_icon);
                vh.tvName = (TextView) v.findViewById(R.id.user_name);
                vh.tvScreenName = (TextView) v.findViewById(R.id.user_sn);
                v.setTag(vh);
            }
            else {
                vh = (ViewHolder) v.getTag();
            }

            Data d = getItem(position);
            if (d != null) {
                vh.tvName.setText(d.name);
                vh.tvScreenName.setText("@" + d.sn);
                vh.ivIcon.setImageResource(R.drawable.ic_launcher);
                vh.ivIcon.setImageUrl(d.imageURL);
            }

            return v;
        }

        private class ViewHolder {
            SmartImageView ivIcon;
            TextView tvScreenName;
            TextView tvName;
        }
    }
}
