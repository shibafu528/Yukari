package shibafu.yukari.activity;

import android.content.res.Resources;
import android.media.MediaScannerConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.Preference;
import android.support.annotation.RawRes;
import android.widget.Toast;
import com.github.machinarius.preferencefragment.PreferenceFragment;
import shibafu.yukari.R;
import shibafu.yukari.activity.base.ActionBarYukariBase;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by shibafu on 14/04/03.
 */
public class CommandsPrefActivity extends ActionBarYukariBase {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_parent);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.frame, new InnerFragment())
                .commit();
    }

    @Override
    public void onServiceConnected() {}

    @Override
    public void onServiceDisconnected() {}

    public static class InnerFragment extends PreferenceFragment {
        private final File mediaDir = new File(Environment.getExternalStorageDirectory(), "Android/media/shibafu.yukari/Notifications");
        private final Resource[] exportResources = {
                new Resource(R.raw.y_reply, new File(mediaDir, "Yukari - Yukari Reply.ogg")),
                new Resource(R.raw.y_fav, new File(mediaDir, "Yukari - Yukari Favorite.ogg")),
                new Resource(R.raw.y_like, new File(mediaDir, "Yukari - Yukari Like.ogg")),
                new Resource(R.raw.y_love, new File(mediaDir, "Yukari - Yukari Love.ogg")),
                new Resource(R.raw.y_rt, new File(mediaDir, "Yukari - Yukari Retweet.ogg")),
                new Resource(R.raw.akari_reply, new File(mediaDir, "Yukari - Akari Reply.ogg")),
                new Resource(R.raw.akari_fav, new File(mediaDir, "Yukari - Akari Favorite.ogg")),
                new Resource(R.raw.akari_like, new File(mediaDir, "Yukari - Akari Like.ogg")),
                new Resource(R.raw.akari_love, new File(mediaDir, "Yukari - Akari Love.ogg")),
                new Resource(R.raw.akari_retweet, new File(mediaDir, "Yukari - Akari Retweet.ogg")),
                new Resource(R.raw.kiri_reply, new File(mediaDir, "Yukari - Kiri Reply.ogg")),
                new Resource(R.raw.kiri_like, new File(mediaDir, "Yukari - Kiri Like.ogg")),
                new Resource(R.raw.kiri_suki, new File(mediaDir, "Yukari - Kiri Love.ogg")),
                new Resource(R.raw.kiri_retweet, new File(mediaDir, "Yukari - Kiri Retweet.ogg")),
        };

        @Override
        public void onCreate(Bundle paramBundle) {
            super.onCreate(paramBundle);
            addPreferencesFromResource(R.xml.commands);

            findPreference("pref_sound_theme").setEnabled(Build.VERSION.SDK_INT < Build.VERSION_CODES.O);

            Preference prefSoundThemeExport = findPreference("pref_sound_theme_export");
            prefSoundThemeExport.setEnabled(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O);
            prefSoundThemeExport.setOnPreferenceClickListener(preference -> {
                Resources res = getResources();

                try {
                    mediaDir.mkdirs();

                    List<String> resourceAbsolutePaths = new ArrayList<>();
                    for (Resource resource : exportResources) {
                        try (InputStream input = res.openRawResource(resource.resId);
                             OutputStream output = new FileOutputStream(resource.file)) {
                            byte[] buf = new byte[4096];
                            int length;
                            while ((length = input.read(buf)) != -1) {
                                output.write(buf, 0, length);
                            }
                        }

                        resourceAbsolutePaths.add(resource.file.getAbsolutePath());
                    }

                    MediaScannerConnection.scanFile(getActivity(),
                            resourceAbsolutePaths.toArray(new String[resourceAbsolutePaths.size()]),
                            null, null);

                    Toast.makeText(getActivity(), "エクスポートが完了しました。", Toast.LENGTH_SHORT).show();
                } catch (IOException e) {
                    e.printStackTrace();
                    Toast.makeText(getActivity(), "エクスポート中にエラーが発生しました。", Toast.LENGTH_SHORT).show();
                }
                return true;
            });
        }

        private static class Resource {
            int resId;
            File file;

            Resource(@RawRes int resId, File file) {
                this.resId = resId;
                this.file = file;
            }
        }
    }
}
