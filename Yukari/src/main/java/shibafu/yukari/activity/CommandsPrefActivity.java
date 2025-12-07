package shibafu.yukari.activity;

import android.content.Context;
import android.content.res.Resources;
import android.media.MediaScannerConnection;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.RawRes;
import androidx.preference.Preference;

import com.takisoft.preferencex.PreferenceFragmentCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import shibafu.yukari.R;
import shibafu.yukari.activity.base.ActionBarYukariBase;

/**
 * Created by shibafu on 14/04/03.
 */
public class CommandsPrefActivity extends ActionBarYukariBase {
    private static final String LOG_TAG = "CommandsPrefActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_parent);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.frame, new InnerFragment())
                .commit();
    }

    public static class InnerFragment extends PreferenceFragmentCompat {
        private final Resource[] exportResources = {
                new Resource(R.raw.y_reply, "Yukari - Yukari Reply.ogg"),
                new Resource(R.raw.y_fav, "Yukari - Yukari Favorite.ogg"),
                new Resource(R.raw.y_like, "Yukari - Yukari Like.ogg"),
                new Resource(R.raw.y_love, "Yukari - Yukari Love.ogg"),
                new Resource(R.raw.y_rt, "Yukari - Yukari Retweet.ogg"),
                new Resource(R.raw.akari_reply, "Yukari - Akari Reply.ogg"),
                new Resource(R.raw.akari_fav, "Yukari - Akari Favorite.ogg"),
                new Resource(R.raw.akari_like, "Yukari - Akari Like.ogg"),
                new Resource(R.raw.akari_love, "Yukari - Akari Love.ogg"),
                new Resource(R.raw.akari_retweet, "Yukari - Akari Retweet.ogg"),
                new Resource(R.raw.kiri_reply, "Yukari - Kiri Reply.ogg"),
                new Resource(R.raw.kiri_like, "Yukari - Kiri Like.ogg"),
                new Resource(R.raw.kiri_suki, "Yukari - Kiri Love.ogg"),
                new Resource(R.raw.kiri_retweet, "Yukari - Kiri Retweet.ogg"),
        };

        @Override
        public void onCreatePreferencesFix(@Nullable Bundle savedInstanceState, String rootKey) {
            addPreferencesFromResource(R.xml.commands);

            Preference prefSoundThemeExport = findPreference("pref_sound_theme_export");
            prefSoundThemeExport.setOnPreferenceClickListener(preference -> {
                exportResources();
                return true;
            });
        }

        void exportResources() {
            Context context = requireContext();
            File[] mediaDirs = context.getExternalMediaDirs();
            if (mediaDirs.length == 0) {
                Log.e(LOG_TAG, "Couldn't find external media directories.");
                Toast.makeText(getActivity(), "エクスポート中にエラーが発生しました。", Toast.LENGTH_SHORT).show();
                return;
            }

            Resources res = getResources();
            try {
                File mediaDir = new File(mediaDirs[0], "Notifications");
                mediaDir.mkdirs();

                List<String> resourceAbsolutePaths = new ArrayList<>();
                for (Resource resource : exportResources) {
                    File file = resource.getFile(mediaDir);
                    try (InputStream input = res.openRawResource(resource.resId);
                         OutputStream output = new FileOutputStream(file)) {
                        byte[] buf = new byte[4096];
                        int length;
                        while ((length = input.read(buf)) != -1) {
                            output.write(buf, 0, length);
                        }
                    }

                    resourceAbsolutePaths.add(file.getAbsolutePath());
                }

                MediaScannerConnection.scanFile(getActivity(),
                        resourceAbsolutePaths.toArray(new String[resourceAbsolutePaths.size()]),
                        null, null);

                Toast.makeText(getActivity(), "エクスポートが完了しました。", Toast.LENGTH_SHORT).show();
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(getActivity(), "エクスポート中にエラーが発生しました。", Toast.LENGTH_SHORT).show();
            }
        }

        private static class Resource {
            int resId;
            String file;

            Resource(@RawRes int resId, String file) {
                this.resId = resId;
                this.file = file;
            }

            File getFile(File parent) {
                return new File(parent, this.file);
            }
        }
    }
}
