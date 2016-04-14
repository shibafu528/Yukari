package info.shibafu528.yukari.exvoice;

/**
 * Created by shibafu on 2016/04/10.
 */
public class Plugin {
    private MRuby mRuby;

    static {
        System.loadLibrary("exvoice");
    }

    public Plugin(MRuby mRuby) {
        this.mRuby = mRuby;
    }

    public static native Object[] filtering(MRuby mRuby, String eventName, Object... args);
}
