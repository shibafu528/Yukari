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
 * Created by shibafu on 2016/04/10.
 */
public abstract class Plugin {
    private final String slug;
    private MRuby mRuby;
    private Map<String, PluggaloidEventListener> listeners = new HashMap<>();

    static {
        System.loadLibrary("exvoice");
    }

    public Plugin(MRuby mRuby, String slug) {
        this.mRuby = mRuby;
        this.slug = slug;
        initialize();
    }

    /*package*/ void onEvent(String eventName, Object... args) {
        Log.d("Plugin", String.format("%s : on_%s", slug, eventName));
        PluggaloidEventListener l = listeners.get(eventName);
        if (l != null) {
            l.onEvent(args);
        }
    }

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
                            return (Object[]) method.invoke(Plugin.this, args);
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

    public String getSlug() {
        return slug;
    }

    protected Context getContext() {
        return mRuby.getContext();
    }

    protected void addEventListener(String eventName, PluggaloidEventListener listener) {
        if (listeners.get(eventName) == null) {
            listeners.put(eventName, listener);
            addEventListenerNative(eventName);
        }
    }

    protected native void addEventListenerNative(String eventName);

    protected native void addEventFilter(String eventName, PluggaloidEventFilter filter);

    private native void initializeNative();

    /**
     * {@code Pluggaloid::Plugin.filtering} を呼び出し、引数 args をフィルタリングした結果を返します。
     * @param mRuby {@link MRuby} のインスタンス
     * @param eventName イベント名
     * @param args 引数
     * @return フィルタされた結果の配列
     */
    public static native Object[] filtering(MRuby mRuby, String eventName, Object... args);

    public interface PluggaloidEventListener {
        void onEvent(Object... args);
    }

    public interface PluggaloidEventFilter {
        Object[] filter(Object... args);
    }
}
