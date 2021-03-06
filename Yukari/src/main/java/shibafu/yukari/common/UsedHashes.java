package shibafu.yukari.common;

import android.content.Context;

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
public class UsedHashes {
    private static final int LIMIT = 16;
    public static final String FILE_NAME = "usedhash.txt";
    private LinkedList<String> cache = new LinkedList<>();

    public UsedHashes(Context context) {
        //既存データのロードを試みる
        File storeFile = new File(context.getFilesDir(), FILE_NAME);
        if (storeFile.exists()) {
            try {
                FileReader fr = new FileReader(storeFile);
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
        //データディレクトリにセーブを試みる
        File storeFile = new File(context.getFilesDir(), FILE_NAME);
        try {
            FileWriter fw = new FileWriter(storeFile);
            BufferedWriter bw = new BufferedWriter(fw);
            for (String s : cache) {
                bw.write(s);
                bw.newLine();
            }
            bw.close();
            fw.close();
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
    }

    public List<String> getAll() {
        return cache;
    }
}
