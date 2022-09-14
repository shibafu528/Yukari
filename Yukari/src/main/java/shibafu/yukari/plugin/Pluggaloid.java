package shibafu.yukari.plugin;

import android.content.Context;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import java.io.File;

import info.shibafu528.yukari.exvoice.MRuby;
import info.shibafu528.yukari.exvoice.miquire.Miquire;
import info.shibafu528.yukari.exvoice.miquire.MiquireResult;
import info.shibafu528.yukari.exvoice.pluggaloid.Plugin;

public class Pluggaloid {
    private final MRuby mRuby;
    private final PluggaloidLogger logger = new PluggaloidLogger();

    public Pluggaloid(Context context) {
        // MRuby VMの初期化
        mRuby = new MRuby(context);
        // 標準出力をLogcatとStringBufferにリダイレクト
        mRuby.setPrintCallback(value -> {
            if (value == null || value.length() == 0 || "\n".equals(value)) {
                return;
            }
            Log.d("ExVoice (TS)", value);
            logger.log(value);
        });
        // ブートストラップの実行およびバンドルRubyプラグインのロード
        mRuby.requireAssets("bootstrap.rb");
        Miquire.loadAll(mRuby);
        // Javaプラグインのロード
        mRuby.registerPlugin(AndroidCompatPlugin.class);
        mRuby.registerPlugin(VirtualWorldPlugin.class);
        // ユーザプラグインのロード
        // TODO: ホワイトリストが必要だよねー
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            File pluginDir = new File(context.getExternalFilesDir(null), "plugin");
            if (pluginDir.exists() && !pluginDir.isDirectory()) {
                logger.log("plugin directory was not found, but found a regular file named `plugin`.");
                Toast.makeText(context, "exvoice プラグインの読み込みでエラーが発生しました", Toast.LENGTH_SHORT).show();
            } else {
                // プラグインディレクトリがなければ作っておく
                if (!pluginDir.exists()) {
                    pluginDir.mkdirs();
                }
                Miquire.appendLoadPath(mRuby, pluginDir.getAbsolutePath());
                MiquireResult result = Miquire.loadAll(mRuby);
                if (result.getFailure().length > 0) {
                    StringBuilder sb = new StringBuilder("プラグインの読み込みに失敗しました");
                    for (String slug : result.getFailure()) {
                        sb.append("\n");
                        sb.append(slug);
                    }
                    logger.log(sb);
                    Toast.makeText(context, "exvoice プラグインの読み込みでエラーが発生しました", Toast.LENGTH_SHORT).show();
                }
            }
        }
        Plugin.call(mRuby, "boot");
    }

    public MRuby getmRuby() {
        return mRuby;
    }

    public PluggaloidLogger getLogger() {
        return logger;
    }

    public void close() {
        mRuby.close();
    }
}
