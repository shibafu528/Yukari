package shibafu.yukari.activity;

import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.preference.PreferenceManager;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.ListFragment;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import shibafu.yukari.R;
import shibafu.yukari.common.FontAsset;
import shibafu.yukari.fragment.SimpleAlertDialogFragment;
import shibafu.yukari.util.ThemeUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Created by shibafu on 14/06/22.
 */
public class FontSelectorActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeUtil.setActivityTheme(this);
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

    public static class InnerFragment extends ListFragment implements AdapterView.OnItemLongClickListener, SimpleAlertDialogFragment.OnDialogChoseListener {
        private final static int DIALOG_DELETE = 1;
        private final static String EXTRA_DELETE_FILENAME = "filename";
        private final static String EXTRA_DELETE_RESET_SELECTION = "reset_selection";

        private FontsAdapter adapter;
        private List<Pair<String, Typeface>> fonts = new ArrayList<>();
        private int selectedIndex = 0;
        private SharedPreferences preferences;

        private final ActivityResultLauncher<String[]> fontFilePicker = registerForActivityResult(new ActivityResultContracts.OpenDocument(), result -> {
            if (result == null) {
                return;
            }

            ContentResolver cr = requireContext().getContentResolver();
            File fontDir = ensureFontDir();

            DocumentFile inputFile = DocumentFile.fromSingleUri(requireContext(), result);
            try (InputStream input = cr.openInputStream(result)) {
                if (inputFile == null || input == null) {
                    Toast.makeText(requireContext(), "ファイルを開けませんでした", Toast.LENGTH_SHORT).show();
                    return;
                }

                String outputFileName = inputFile.getName();
                if (outputFileName == null) {
                    outputFileName = UUID.randomUUID().toString();
                }
                if (!(outputFileName.endsWith(".ttf") || outputFileName.endsWith(".otf"))) {
                    String type = inputFile.getType();
                    if (type != null) {
                        switch (type) {
                            case "font/ttf":
                                outputFileName += ".ttf";
                                break;
                            case "font/otf":
                                outputFileName += ".otf";
                                break;
                        }
                    }
                }
                if (!(outputFileName.endsWith(".ttf") || outputFileName.endsWith(".otf"))) {
                    outputFileName += ".ttf";
                }

                File outputFile = new File(fontDir, outputFileName);
                try (OutputStream output = new FileOutputStream(outputFile)) {
                    byte[] buffer = new byte[4096];
                    int read;
                    while ((read = input.read(buffer, 0, buffer.length)) != -1) {
                        output.write(buffer, 0, read);
                    }
                }

                reloadFonts();
                adapter.notifyDataSetChanged();

                Toast.makeText(requireContext(), "インポートしました", Toast.LENGTH_SHORT).show();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                Toast.makeText(requireContext(), "ファイルを開けませんでした", Toast.LENGTH_SHORT).show();
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(requireContext(), "インポート中にエラーが発生しました", Toast.LENGTH_SHORT).show();
            }
        });

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setHasOptionsMenu(true);
        }

        @Override
        public void onViewCreated(View view, Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);

            preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
            reloadFonts();
            adapter = new FontsAdapter(getActivity(), fonts);
            setListAdapter(adapter);

            getListView().setOnItemLongClickListener(this);
        }

        private void reloadFonts() {
            String currentFile = preferences.getString("pref_font_file", FontAsset.BUNDLE_FONT_ID);

            selectedIndex = 0;
            fonts.clear();
            fonts.add(new Pair<>(FontAsset.BUNDLE_FONT_ID, FontAsset.getBundleFont(getContext().getAssets())));
            fonts.add(new Pair<>(FontAsset.SYSTEM_FONT_ID, Typeface.DEFAULT));
            if (FontAsset.SYSTEM_FONT_ID.equals(currentFile)) {
                selectedIndex = 1;
            }

            File fontDir = ensureFontDir();
            File[] files = fontDir.listFiles((dir, filename) -> filename.endsWith(".ttf") || filename.endsWith(".otf"));
            if (files != null) {
                for (int i = 0; i < files.length; i++) {
                    File file = files[i];
                    try {
                        Typeface typeface = Typeface.createFromFile(file);
                        fonts.add(new Pair<>(file.getName(), typeface));
                        if (file.getName().equals(currentFile)) {
                            selectedIndex = i + 2;
                        }
                    } catch (RuntimeException e) {
                        //native typeface cannot be made
                        e.printStackTrace();
                        fonts.add(new Pair<>(file.getName(), null));
                    }
                }
            }
        }

        @NonNull
        private File ensureFontDir() {
            File fontDir = new File(requireContext().getExternalFilesDir(null), "font");
            if (!fontDir.exists()) {
                fontDir.mkdirs();
            }
            return fontDir;
        }

        @Override
        public void onListItemClick(ListView l, View v, int position, long id) {
            super.onListItemClick(l, v, position, id);
            selectedIndex = position;
            adapter.notifyDataSetChanged();
            preferences.edit().putString("pref_font_file", fonts.get(position).first).commit();
            FontAsset.reloadInstance(getActivity());
        }

        @Override
        public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
            String name = fonts.get(position).first;
            if (FontAsset.BUNDLE_FONT_ID.equals(name) || FontAsset.SYSTEM_FONT_ID.equals(name)) {
                return false;
            }

            Bundle extras = new Bundle();
            extras.putString(EXTRA_DELETE_FILENAME, name);
            extras.putBoolean(EXTRA_DELETE_RESET_SELECTION, selectedIndex == position);
            SimpleAlertDialogFragment dialogFragment = new SimpleAlertDialogFragment.Builder(DIALOG_DELETE)
                    .setTitle("確認")
                    .setMessage(String.format(Locale.US, "フォント %s を削除してもよろしいですか？", name))
                    .setPositive("OK")
                    .setNegative("キャンセル")
                    .setExtras(extras)
                    .build();
            dialogFragment.setTargetFragment(this, DIALOG_DELETE);
            dialogFragment.show(requireFragmentManager(), "dialog");
            return true;
        }

        @Override
        public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
            MenuItem addMenu = menu.add(Menu.NONE, R.id.action_add, Menu.NONE, "インポート").setIcon(R.drawable.ic_action_add);
            addMenu.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        }

        @Override
        public boolean onOptionsItemSelected(@NonNull MenuItem item) {
            if (item.getItemId() == R.id.action_add) {
                fontFilePicker.launch(new String[]{"font/ttf", "font/otf"});
                return true;
            }

            return super.onOptionsItemSelected(item);
        }

        @Override
        public void onDialogChose(int requestCode, int which, @Nullable Bundle extras) {
            if (requestCode == DIALOG_DELETE && which == DialogInterface.BUTTON_POSITIVE) {
                String name = extras.getString(EXTRA_DELETE_FILENAME);
                boolean resetSelection = extras.getBoolean(EXTRA_DELETE_RESET_SELECTION);

                File fontDir = ensureFontDir();
                File file = new File(fontDir, name);
                file.delete();

                if (resetSelection) {
                    preferences.edit().putString("pref_font_file", FontAsset.BUNDLE_FONT_ID).apply();
                    FontAsset.reloadInstance(getActivity());
                }

                reloadFonts();
                adapter.notifyDataSetChanged();
            }
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
                    text.setText("She unites you all with her voice.\n✌('ω'✌ )三✌('ω')✌三( ✌'ω')✌");
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
                if (FontAsset.SYSTEM_FONT_ID.equals(item.first)) {
                    vh.radio.setText("System Font");
                } else if (FontAsset.BUNDLE_FONT_ID.equals(item.first)) {
                    vh.radio.setText("Bundle Font");
                } else {
                    vh.radio.setText(item.first);
                }
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
