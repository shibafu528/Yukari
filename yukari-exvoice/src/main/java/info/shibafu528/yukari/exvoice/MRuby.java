package info.shibafu528.yukari.exvoice;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by shibafu on 2016/03/28.
 */
public class MRuby {
    static {
        System.loadLibrary("exvoice");
    }

    private long mrubyInstancePointer;
    private Context context;
    private AssetManager assetManager;
    private PrintCallback printCallback;
    private Map<String, Plugin> plugins = new HashMap<>();

    /**
     * MRubyのVMを初期化し、使用可能な状態にします。
     * @param context
     */
    public MRuby(Context context) {
        mrubyInstancePointer = n_open();
        this.context = context;
        this.assetManager = context.getAssets();
    }

    /**
     * MRubyのVMをシャットダウンします。
     */
    public void close() {
        if (mrubyInstancePointer == 0) {
            throw new IllegalStateException("MRuby VM was already closed.");
        }
        n_close(mrubyInstancePointer);
        mrubyInstancePointer = 0;
    }

    /**
     * 引数として渡された文字列をRubyプログラムとしてトップレベルのコンテキストで実行します。
     * @param code Rubyプログラム
     */
    public void loadString(String code) {
        loadString(code, true);
    }

    /**
     * 引数として渡された文字列をRubyプログラムとしてトップレベルのコンテキストで実行します。
     * @param code Rubyプログラム
     * @param echo 入力をLogcat上にエコーします
     */
    public void loadString(String code, boolean echo) {
        n_loadString(mrubyInstancePointer, code, echo);
    }

    /**
     * 指定の名前のメソッドをトップレベルから検索し実行します。
     * @param name メソッド名
     * @return メソッドの返り値
     */
    public Object callTopLevelFunc(String name) {
        return n_callTopLevelFunc(mrubyInstancePointer, name);
    }

    /**
     * プラグインを登録し、使用可能な状態にします。
     * @param clazz プラグインクラス
     */
    public void registerPlugin(Class<? extends Plugin> clazz) {
        try {
            Constructor<? extends Plugin> constructor = clazz.getConstructor(MRuby.class);
            Plugin plugin = constructor.newInstance(this);
            plugins.put(plugin.getSlug(), plugin);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    public void setPrintCallback(PrintCallback printCallback) {
        this.printCallback = printCallback;
    }

    public void printStringCallback(String value) {
        if (printCallback != null) {
            printCallback.print(value);
        } else {
            Log.d("exvoice(Java)", value);
        }
    }

    /*package*/ long getMRubyInstancePointer() {
        return mrubyInstancePointer;
    }

    /*package*/ Context getContext() {
        return context;
    }

    /*package*/ Plugin getPlugin(String slug) {
        return plugins.get(slug);
    }

    private native long n_open();
    private native void n_close(long mrb);

    private native void n_loadString(long mrb, String code, boolean echo);
    private native Object n_callTopLevelFunc(long mrb, String name);

    public interface PrintCallback {
        void print(String value);
    }
}