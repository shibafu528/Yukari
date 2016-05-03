package info.shibafu528.yukari.exvoice;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * {@code Pluggaloid::Plugin} と対応する機能を提供します。
 *
 * Created by shibafu on 2016/04/10.
 */
public abstract class Plugin {
    static {
        System.loadLibrary("exvoice");
    }

    private final String slug;
    private MRuby mRuby;
    private Map<String, PluggaloidEventListener> listeners = new HashMap<>();
    private Map<String, PluggaloidEventFilter> filters = new HashMap<>();

    /**
     * プラグインの初期化を行います。サブクラスではここでslugを決定し、{@link MRuby} のみを引数にとる必要があります。
     * @param mRuby MRubyインスタンス
     * @param slug 識別名 (slug)
     */
    public Plugin(MRuby mRuby, String slug) {
        this.mRuby = mRuby;
        this.slug = slug;
        initialize();
    }

    /**
     * {@code Pluggaloid::Plugin.call} を呼び出します。
     * @param mRuby {@link MRuby} のインスタンス
     * @param eventName イベント名
     * @param args 引数
     */
    public static native void call(MRuby mRuby, String eventName, Object... args);

    /**
     * {@code Pluggaloid::Plugin.filtering} を呼び出し、引数 args をフィルタリングした結果を返します。
     * @param mRuby {@link MRuby} のインスタンス
     * @param eventName イベント名
     * @param args 引数
     * @return フィルタされた結果の配列
     * @exception FilterException 引数の数が途中で変化した場合に発生します。
     */
    public static native Object[] filtering(MRuby mRuby, String eventName, Object... args);

    /**
     * プラグインの識別名(slug)を取得します。
     * @return slug
     */
    public String getSlug() {
        return slug;
    }

    /**
     * イベントが発生した際、ネイティブのコールバック関数から呼び出されます。
     * @param eventName イベント名
     * @param args 引数
     */
    /*package*/ void onEvent(String eventName, Object... args) {
        Log.d("Plugin", String.format("%s : on_%s", slug, eventName));

        PluggaloidEventListener l = listeners.get(eventName);
        if (l != null) {
            l.onEvent(args);
        }
    }

    /**
     * イベントフィルタが呼びだされた際、ネイティブのコールバック関数から呼び出されます。
     * @param eventName イベント名
     * @param args 引数
     * @return フィルタされた結果の配列
     */
    /*package*/ Object[] filter(String eventName, Object... args) {
        Log.d("Plugin", String.format("%s : filter_%s", slug, eventName));

        PluggaloidEventFilter f = filters.get(eventName);
        if (f != null) {
            return f.filter(args);
        } else {
            return args;
        }
    }

    /**
     * MRubyを所有しているContextを取得します。
     * @return Context
     */
    protected Context getContext() {
        return mRuby.getContext();
    }

    /**
     * Pluggaloidイベントを受信するリスナを登録します。同じ名前のイベントに対して複数登録することはできません。
     * @param eventName イベント名
     * @param listener リスナ
     */
    protected void addEventListener(String eventName, PluggaloidEventListener listener) {
        if (listeners.get(eventName) == null) {
            listeners.put(eventName, listener);
            addEventListenerNative(eventName);
        }
    }

    /**
     * Pluggaloidイベントフィルタを処理するリスナを登録します。同じ名前のフィルタに対して複数登録することはできません。
     * @param eventName イベント名
     * @param filter フィルタ
     */
    protected void addEventFilter(String eventName, PluggaloidEventFilter filter) {
        if (filters.get(eventName) == null) {
            filters.put(eventName, filter);
            addEventFilterNative(eventName);
        }
    }

    /**
     * プラグインの初期化を行います。また、ここでアノテーションの付いたコールバックを有効にします。
     */
    private void initialize() {
        // Pluggaloidでのプラグイン宣言とコールバックの登録
        initializeNative();

        // イベントの登録
        for (final Method method : getClass().getDeclaredMethods()) {
            if (method.isAnnotationPresent(Event.class)) {
                Event annotation = method.getAnnotation(Event.class);
                String eventName;
                if (TextUtils.isEmpty(annotation.value())) {
                    eventName = method.getName();
                } else {
                    eventName = annotation.value();
                }

                addEventListener(eventName, new PluggaloidEventListener() {
                    @Override
                    public void onEvent(Object... args) {
                        try {
                            method.invoke(Plugin.this, Arrays.copyOf(args, method.getParameterTypes().length));
                        } catch (IllegalAccessException e) {
                            e.printStackTrace();
                        } catch (InvocationTargetException e) {
                            e.printStackTrace();
                        }
                    }
                });
            } else if (method.isAnnotationPresent(Filter.class)) {
                Filter annotation = method.getAnnotation(Filter.class);
                String eventName;
                if (TextUtils.isEmpty(annotation.value())) {
                    eventName = method.getName();
                } else {
                    eventName = annotation.value();
                }

                addEventFilter(eventName, new PluggaloidEventFilter() {
                    @Override
                    public Object[] filter(Object... args) {
                        try {
                            return (Object[]) method.invoke(Plugin.this, Arrays.copyOf(args, method.getParameterTypes().length));
                        } catch (IllegalAccessException e) {
                            e.printStackTrace();
                        } catch (InvocationTargetException e) {
                            e.printStackTrace();
                        }
                        return new Object[args.length];
                    }
                });
            }
        }
    }

    /**
     * ネイティブに降りて、Pluggaloidプラグインの初期化を行います。
     */
    private native void initializeNative();

    /**
     * ネイティブに降りて、Pluggaloidプラグインにイベントリスナを登録します。
     * @param eventName イベント名
     */
    private native void addEventListenerNative(String eventName);

    /**
     * ネイティブに降りて、Pluggaloidプラグインにイベントフィルタを登録します。
     * @param eventName
     */
    private native void addEventFilterNative(String eventName);

    public interface PluggaloidEventListener {
        void onEvent(Object... args);
    }

    public interface PluggaloidEventFilter {
        Object[] filter(Object... args);
    }
}
