package shibafu.yukari.activity;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.ListFragment;
import android.support.v7.app.ActionBarActivity;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.TextView;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.List;

import shibafu.yukari.af2015.R;
import shibafu.yukari.common.FontAsset;

/**
 * Created by shibafu on 14/06/22.
 */
public class FontSelectorActivity extends ActionBarActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        switch (PreferenceManager.getDefaultSharedPreferences(this).getString("pref_theme", "light")) {
            case "light":
                setTheme(R.style.YukariLightTheme);
                break;
            case "dark":
                setTheme(R.style.YukariDarkTheme);
                break;
        }
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_parent);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.frame, new InnerFragment())
                    .commit();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public static class InnerFragment extends ListFragment {

        private FontsAdapter adapter;
        private List<Pair<String, Typeface>> fonts = new ArrayList<>();
        private int selectedIndex = 0;
        private SharedPreferences preferences;

        @Override
        public void onViewCreated(View view, Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);

            preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
            String currentFile = preferences.getString("pref_font_file", FontAsset.FONT_NAME);

            fonts.add(new Pair<>(FontAsset.SYSTEM_FONT_ID, Typeface.DEFAULT));

            File fontDir = new File(getActivity().getExternalFilesDir(null), "font");
            File[] files = fontDir.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String filename) {
                    return filename.endsWith(".ttf") || filename.endsWith(".otf");
                }
            });
            for (int i = 0; i < files.length; i++) {
                File file = files[i];
                try {
                    Typeface typeface = Typeface.createFromFile(file);
                    fonts.add(new Pair<>(file.getName(), typeface));
                    if (file.getName().equals(currentFile)) {
                        selectedIndex = i + 1;
                    }
                } catch (RuntimeException e) {
                    //native typeface cannot be made
                    e.printStackTrace();
                    fonts.add(new Pair<String, Typeface>(file.getName(), null));
                }
            }

            adapter = new FontsAdapter(getActivity(), fonts);
            setListAdapter(adapter);
        }

        @Override
        public void onListItemClick(ListView l, View v, int position, long id) {
            super.onListItemClick(l, v, position, id);
            selectedIndex = position;
            adapter.notifyDataSetChanged();
            preferences.edit().putString("pref_font_file", fonts.get(position).first).commit();
            FontAsset.reloadInstance(getActivity());
        }

        private class FontsAdapter extends ArrayAdapter<Pair<String, Typeface>> {
            private class ViewHolder {
                RadioButton radio;
                View tweetView;

                ImageView icon;
                TextView name;
                TextView text;
                TextView timestamp;

                private ViewHolder(View view) {
                    radio = (RadioButton) view.findViewById(R.id.radio);
                    tweetView = view.findViewById(R.id.tweet);
                    icon = (ImageView) tweetView.findViewById(R.id.tweet_icon);
                    name = (TextView) tweetView.findViewById(R.id.tweet_name);
                    text = (TextView) tweetView.findViewById(R.id.tweet_text);
                    timestamp = (TextView) tweetView.findViewById(R.id.tweet_timestamp);
                    icon.setImageResource(R.drawable.ic_launcher);
                    tweetView.setBackgroundResource(R.drawable.selector_tweet_normal_background);

                    name.setText("@yukari4a / ﾕｯｶﾘｰﾝ");
                    text.setText("Yuzuki Yukari is very cute girl!\n✌('ω'✌ )三✌('ω')✌三( ✌'ω')✌");
                }
            }

            private LayoutInflater inflater;

            public FontsAdapter(Context context, List<Pair<String, Typeface>> objects) {
                super(context, 0, objects);
                inflater = (LayoutInflater) context.getSystemService(LAYOUT_INFLATER_SERVICE);
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                ViewHolder vh;
                if (convertView == null) {
                    convertView = inflater.inflate(R.layout.row_font, null);
                    vh = new ViewHolder(convertView);
                    convertView.setTag(vh);
                } else {
                    vh = (ViewHolder) convertView.getTag();
                }
                if (selectedIndex == position) {
                    vh.radio.setChecked(true);
                } else {
                    vh.radio.setChecked(false);
                }
                Pair<String, Typeface> item = getItem(position);
                vh.radio.setText(FontAsset.SYSTEM_FONT_ID.equals(item.first)? "System Font" : item.first);
                Typeface tf = item.second;
                if (tf != null) {
                    vh.radio.setEnabled(true);
                    vh.name.setTypeface(tf);
                    vh.text.setTypeface(tf);
                    vh.timestamp.setTypeface(tf);
                } else {
                    vh.radio.setEnabled(false);
                    vh.radio.setText("*Broken* " + vh.radio.getText());
                }

                return convertView;
            }
        }
    }
}
