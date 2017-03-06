package shibafu.yukari.activity;

import android.content.Intent;
import android.content.res.AssetManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import butterknife.BindView;
import butterknife.ButterKnife;
import shibafu.yukari.R;
import shibafu.yukari.common.FontAsset;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Created by Shibafu on 13/08/07.
 */
public class AssetExtractActivity extends FragmentActivity{

    @BindView(R.id.progressBar)
    ProgressBar progressBar;

    @BindView(R.id.tvProgress)
    TextView progressText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_asset);
        ButterKnife.bind(this);

        if (savedInstanceState == null) {
            class Progress {
                public long current, max;
            }

            AsyncTask<Void, Progress, Boolean> task = new AsyncTask<Void, Progress, Boolean>() {

                @Override
                protected Boolean doInBackground(Void... params) {
                    try {
                        AssetManager am = getResources().getAssets();
                        InputStream is = am.open(FontAsset.FONT_ZIP, AssetManager.ACCESS_STREAMING);
                        ZipInputStream zis = new ZipInputStream(is);
                        ZipEntry ze = zis.getNextEntry();

                        if (ze != null) {
                            Progress progress = new Progress();

                            String path = FontAsset.getFontFileExtPath(AssetExtractActivity.this, FontAsset.FONT_NAME).getPath();
                            FileOutputStream fos = new FileOutputStream(path, false);
                            byte[] buf = new byte[1024];
                            int size = 0;

                            progress.max = ze.getSize();

                            while ((size = zis.read(buf, 0, buf.length)) > -1) {
                                fos.write(buf, 0, size);
                                progress.current += size;
                                publishProgress(progress);
                            }
                            fos.close();
                            zis.closeEntry();
                            zis.close();
                            return true;
                        }
                        zis.close();
                        return false;
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    return false;
                }

                @Override
                protected void onProgressUpdate(Progress... values) {
                    progressBar.setMax((int) values[0].max);
                    progressBar.setProgress((int) values[0].current);

                    progressText.setText(String.format("%d %%", values[0].current * 100 / values[0].max));
                }

                @Override
                protected void onPostExecute(Boolean aBoolean) {
                    if (!aBoolean) {
                        Toast.makeText(AssetExtractActivity.this, "[Yukari 起動エラー] フォント展開エラー\nフォントの展開に失敗しました\n起動は中断されます", Toast.LENGTH_LONG).show();
                        finish();
                    } else {
                        Toast.makeText(AssetExtractActivity.this, "[Yukari データチェック]\nフォントファイルは正しく準備されました", Toast.LENGTH_LONG).show();
                        startActivity(new Intent(AssetExtractActivity.this, MainActivity.class));
                        finish();
                    }
                }
            };
            task.execute();
        }
    }
}
