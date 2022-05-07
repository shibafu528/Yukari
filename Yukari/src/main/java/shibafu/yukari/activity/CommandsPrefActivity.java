package shibafu.yukari.activity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.Resources;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RawRes;
import androidx.preference.Preference;
import android.widget.Toast;

import com.takisoft.fix.support.v7.preference.PreferenceFragmentCompat;

import permissions.dispatcher.NeedsPermission;
import permissions.dispatcher.OnPermissionDenied;
import permissions.dispatcher.OnShowRationale;
import permissions.dispatcher.PermissionRequest;
import permissions.dispatcher.PermissionUtils;
import permissions.dispatcher.RuntimePermissions;
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

    @RuntimePermissions
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

            findPreference("pref_sound_theme").setEnabled(Build.VERSION.SDK_INT < Build.VERSION_CODES.O);

            Preference prefSoundThemeExport = findPreference("pref_sound_theme_export");
            prefSoundThemeExport.setEnabled(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O);
            prefSoundThemeExport.setOnPreferenceClickListener(preference -> {
                InnerFragmentPermissionsDispatcher.exportResourcesWithPermissionCheck(this);
                return true;
            });
        }

        @SuppressLint("NeedOnRequestPermissionsResult")
        @Override
        public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            InnerFragmentPermissionsDispatcher.onRequestPermissionsResult(this, requestCode, grantResults);
        }

        @NeedsPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        void exportResources() {
            Resources res = getResources();

            try {
                File mediaDir = new File(Environment.getExternalStorageDirectory(), "Android/media/" + requireContext().getPackageName() + "/Notifications");
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

        @OnPermissionDenied(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        void onDeniedWriteExternalStorage() {
            if (PermissionUtils.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_EXTERNAL_STORAGE)) {
                Toast.makeText(getActivity(), "ストレージにアクセスする権限がありません。", Toast.LENGTH_SHORT).show();
            } else {
                new AlertDialog.Builder(getActivity())
                        .setTitle("許可が必要")
                        .setMessage("この操作を実行するためには、手動で設定画面からストレージへのアクセスを許可する必要があります。")
                        .setPositiveButton("設定画面へ", (dialog, which) -> {
                            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                            intent.setData(Uri.fromParts("package", getActivity().getPackageName(), null));
                            startActivity(intent);
                        })
                        .setNegativeButton("今はしない", (dialog, which) -> {})
                        .create()
                        .show();
            }
        }

        @OnShowRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        void showRationaleForWriteExternalStorage(final PermissionRequest request) {
            new AlertDialog.Builder(getActivity())
                    .setTitle("許可が必要")
                    .setMessage("この操作を実行するためには、ストレージへのアクセス許可が必要です。")
                    .setPositiveButton("許可", (dialog, which) -> {
                        request.proceed();
                    })
                    .setNegativeButton("許可しない", (dialog, which) -> {
                        request.cancel();
                    })
                    .create()
                    .show();
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
