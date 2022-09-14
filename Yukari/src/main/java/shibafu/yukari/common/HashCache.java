package shibafu.yukari.common;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by Shibafu on 13/08/06.
 */
public class HashCache {
    private static final int LIMIT = 16;
    public static final String FILE_NAME = "hashtag.txt";

    private final Context context;
    private final LinkedList<String> cache = new LinkedList<>();

    private final BufferedTrigger saveTrigger = new BufferedTrigger(1000) {
        @Override
        public void doProcess() {
            save(context);
        }
    };

    public HashCache(Context context) {
        this.context = context.getApplicationContext();
        //既存データのロードを試みる
        File cacheFile = new File(context.getCacheDir(), FILE_NAME);
        if (cacheFile.exists()) {
            try {
                FileReader fr = new FileReader(cacheFile);
                BufferedReader br = new BufferedReader(fr);
                String s;
                while ((s = br.readLine()) != null) {
                    if (!s.equals("") && !s.equals("\n")) {
                        cache.add(s);
                    }
                }
                br.close();
                fr.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void save(Context context) {
        //キャッシュディレクトリにセーブを試みる
        File cacheFile = new File(context.getCacheDir(), FILE_NAME);
        try {
            FileWriter fw = new FileWriter(cacheFile);
            BufferedWriter bw = new BufferedWriter(fw);
            for (String s : cache) {
                bw.write(s);
                bw.newLine();
            }
            bw.close();
            fw.close();
            Log.d("HashCache", "saved " + FILE_NAME);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void put(String hashtag) {
        if (!hashtag.startsWith("#")) {
            hashtag = "#" + hashtag;
        }
        if (cache.contains(hashtag)) {
            cache.remove(hashtag);
        }
        cache.addFirst(hashtag);
        if (cache.size() > LIMIT) {
            cache.removeLast();
        }
        // バックグラウンドで永続化
        saveTrigger.trigger();
    }

    public List<String> getAll() {
        return cache;
    }
}
