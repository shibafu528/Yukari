package shibafu.yukari.activity;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.text.Editable;
import android.text.TextUtils;
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

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import shibafu.yukari.af2015.R;
import shibafu.yukari.activity.base.FragmentYukariBase;
import shibafu.yukari.common.bitmapcache.ImageLoaderTask;
import shibafu.yukari.database.CentralDatabase;

public class SNPickerActivity extends FragmentYukariBase implements LoaderManager.LoaderCallbacks<List<SNPickerActivity.SuggestedName>> {

    public static final String EXTRA_USER_ID = "user_id";
    public static final String EXTRA_SCREEN_NAME = "screen_name";
    public static final String EXTRA_NAME = "name";

    private TextView tvHead;
    private EditText editText;

    private Adapter adapter;
    private ArrayList<SuggestedName> suggestedNameList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        switch (PreferenceManager.getDefaultSharedPreferences(this).getString("pref_theme", "light")) {
            case "light":
                setTheme(R.style.YukariLightDialogTheme);
                break;
            case "dark":
                setTheme(R.style.YukariDarkDialogTheme);
                break;
        }
        super.onCreate(savedInstanceState, true);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_snpicker);

        adapter = new Adapter(this, suggestedNameList);

        ListView listView = (ListView) findViewById(R.id.lvSNPick);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                SuggestedName d = suggestedNameList.get(position);
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
        String query = editText.getText().toString();
        if (TextUtils.isEmpty(query) || query.length() < 2) {
            suggestedNameList.clear();
            adapter.notifyDataSetChanged();
            tvHead.setText("Suggest");
        } else {
            getSupportLoaderManager().restartLoader(0, null, this);
        }
    }

    @Override
    public void onServiceConnected() {
        updateList();
    }

    @Override
    public void onServiceDisconnected() {}

    @Override
    public Loader<List<SuggestedName>> onCreateLoader(int id, Bundle args) {
        return new ScreenNameAsyncLoader(getApplicationContext(),
                editText.getText().toString(),
                getTwitterService().getDatabase());
    }

    @Override
    public void onLoadFinished(Loader<List<SuggestedName>> loader, List<SuggestedName> suggestedName) {
        suggestedNameList.clear();
        suggestedNameList.addAll(suggestedName);
        adapter.notifyDataSetChanged();
        tvHead.setText("Suggest (" + suggestedNameList.size() + ")");
    }

    @Override
    public void onLoaderReset(Loader<List<SuggestedName>> loader) {}

    static class SuggestedName {
        long id;
        String name;
        String sn;
        String imageURL;

        private SuggestedName(long id, String name, String sn, String imageURL) {
            this.id = id;
            this.name = name;
            this.sn = sn;
            this.imageURL = imageURL;
        }
    }

    private class Adapter extends ArrayAdapter<SuggestedName> {

        private LayoutInflater inflater;

        public Adapter(Context context, List<SuggestedName> objects) {
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

            SuggestedName d = getItem(position);
            if (d != null) {
                vh.tvName.setText(d.name);
                vh.tvScreenName.setText("@" + d.sn);
                ImageLoaderTask.loadProfileIcon(getApplicationContext(), vh.ivIcon, d.imageURL);
            }

            return v;
        }

        private class ViewHolder {
            ImageView ivIcon;
            TextView tvScreenName;
            TextView tvName;
        }
    }

    private static class ScreenNameAsyncLoader extends AsyncTaskLoader<List<SuggestedName>> {
        private String query;
        private WeakReference<CentralDatabase> database;

        public ScreenNameAsyncLoader(Context context, String query, CentralDatabase database) {
            super(context);
            this.query = query;
            this.database = new WeakReference<>(database);
        }

        @Override
        public List<SuggestedName> loadInBackground() {
            List<SuggestedName> suggestedNameList = new ArrayList<>();
            CentralDatabase db = database.get();
            if (db != null) {
                Cursor c;
                if (query.equals("")) {
                    c = db.getUsersCursor();
                } else {
                    query = "%" + query + "%";
                    c = db.getUsersCursor(CentralDatabase.COL_USER_NAME + " LIKE ? OR " + CentralDatabase.COL_USER_SCREEN_NAME + " LIKE ?",
                            new String[]{query, query});
                }
                try {
                    final int rowId = c.getColumnIndex(CentralDatabase.COL_USER_ID);
                    final int rowName = c.getColumnIndex(CentralDatabase.COL_USER_NAME);
                    final int rowSN = c.getColumnIndex(CentralDatabase.COL_USER_SCREEN_NAME);
                    final int rowURL = c.getColumnIndex(CentralDatabase.COL_USER_PROFILE_IMAGE_URL);

                    if (c.moveToFirst()) {
                        do {
                            SuggestedName d = new SuggestedName(c.getLong(rowId), c.getString(rowName), c.getString(rowSN), c.getString(rowURL));
                            suggestedNameList.add(d);
                        } while (c.moveToNext());
                    }
                } finally {
                    c.close();
                }
            }
            return suggestedNameList;
        }

        @Override
        protected void onStartLoading() {
            forceLoad();
        }
    }
}
