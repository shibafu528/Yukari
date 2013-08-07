package shibafu.yukari.activity;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.res.AssetManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.Toast;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import shibafu.yukari.common.FontAsset;

/**
 * Created by Shibafu on 13/08/07.
 */
public class AssetExtractActivity extends Activity{
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setTitle("Extracting font resource...");
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setIndeterminate(false);
        progressDialog.setCancelable(false);

        AsyncTask<Void, Void, Boolean> task = new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(Void... params) {
                try {
                    AssetManager am = getResources().getAssets();
                    InputStream is = am.open(FontAsset.FONT_ZIP, AssetManager.ACCESS_STREAMING);
                    ZipInputStream zis = new ZipInputStream(is);
                    ZipEntry ze = zis.getNextEntry();

                    if (ze != null) {
                        progressDialog.setMax((int)ze.getSize());
                        int loaded = 0;

                        String path = FontAsset.getFontFileExtPath(AssetExtractActivity.this).getPath();
                        FileOutputStream fos = new FileOutputStream(path, false);
                        byte[] buf = new byte[1024];
                        int size = 0;

                        while ((size = zis.read(buf, 0, buf.length)) > -1) {
                            fos.write(buf, 0, size);
                            loaded += size;
                            progressDialog.setProgress(loaded);
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
            protected void onPostExecute(Boolean aBoolean) {
                progressDialog.dismiss();

                if (!aBoolean.booleanValue()) {
                    Toast.makeText(AssetExtractActivity.this, "[Yukari 起動エラー] フォント展開エラー\nフォントの展開に失敗しました\n起動は中断されます", Toast.LENGTH_LONG).show();
                    finish();
                }
                else {
                    Toast.makeText(AssetExtractActivity.this, "[Yukari データチェック]\nフォントファイルは正しく準備されました", Toast.LENGTH_LONG).show();
                    startActivity(new Intent(AssetExtractActivity.this, MainActivity.class));
                    finish();
                }
            }
        };

        task.execute();
        progressDialog.show();
    }
}
