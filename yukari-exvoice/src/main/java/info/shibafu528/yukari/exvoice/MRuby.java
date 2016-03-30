package info.shibafu528.yukari.exvoice;

/**
 * Created by shibafu on 2016/03/28.
 */
public class MRuby {
    private long mrubyInstance;

    static {
        System.loadLibrary("exvoice");
    }

    public MRuby() {
        mrubyInstance = n_open();
    }

    public void close() {
        n_close(mrubyInstance);
    }

    public void loadString(String code) {
        n_loadString(mrubyInstance, code);
    }

    private static native long n_open();
    private static native void n_close(long mrb);

    private static native void n_loadString(long mrb, String code);
}
