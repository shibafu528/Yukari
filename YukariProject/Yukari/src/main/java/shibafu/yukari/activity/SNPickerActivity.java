package shibafu.yukari.activity;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import shibafu.yukari.R;
import shibafu.yukari.activity.base.YukariBase;
import shibafu.yukari.common.bitmapcache.ImageLoaderTask;
import shibafu.yukari.database.CentralDatabase;

public class SNPickerActivity extends YukariBase {

    public static final String EXTRA_USER_ID = "user_id";
    public static final String EXTRA_SCREEN_NAME = "screen_name";
    public static final String EXTRA_NAME = "name";

    private TextView tvHead;
    private EditText editText;

    private Adapter adapter;
    private ArrayList<Data> dataList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_snpicker);

        dataList = new ArrayList<>();
        adapter = new Adapter(this, dataList);

        ListView listView = (ListView) findViewById(R.id.lvSNPick);
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

        tvHead = (TextView) findViewById(R.id.tvSNPickHead);
        editText = (EditText) findViewById(R.id.etSNPickName);
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                updateList();
            }
        });
    }

    private void updateList() {
        CentralDatabase db = getTwitterService().getDatabase();

        String et = editText.getText().toString();
        Cursor c;
        if (et.equals("")) {
            c = db.getUsersCursor();
        }
        else {
            et = "%" + et + "%";
            c = db.getUsersCursor(CentralDatabase.COL_USER_NAME + " LIKE ? OR " + CentralDatabase.COL_USER_SCREEN_NAME + " LIKE ?",
                    new String[]{et, et});
        }

        final int rowId = c.getColumnIndex(CentralDatabase.COL_USER_ID);
        final int rowName = c.getColumnIndex(CentralDatabase.COL_USER_NAME);
        final int rowSN = c.getColumnIndex(CentralDatabase.COL_USER_SCREEN_NAME);
        final int rowURL = c.getColumnIndex(CentralDatabase.COL_USER_PROFILE_IMAGE_URL);

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
    }

    @Override
    public void onServiceConnected() {
        updateList();
    }

    @Override
    public void onServiceDisconnected() {

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
                vh.ivIcon = (ImageView) v.findViewById(R.id.user_icon);
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
                ImageLoaderTask.loadProfileIcon(SNPickerActivity.this, vh.ivIcon, d.imageURL);
            }

            return v;
        }

        private class ViewHolder {
            ImageView ivIcon;
            TextView tvScreenName;
            TextView tvName;
        }
    }
}