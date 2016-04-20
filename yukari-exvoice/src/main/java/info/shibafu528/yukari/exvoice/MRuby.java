package info.shibafu528.yukari.exvoice;

import android.content.res.AssetManager;

/**
 * Created by shibafu on 2016/03/28.
 */
public class MRuby {
    private long mrubyInstancePointer;
    private AssetManager assetManager;
    private PrintCallback printCallback;

    static {
        System.loadLibrary("exvoice");
    }

    public MRuby(AssetManager assetManager) {
        mrubyInstancePointer = n_open();
        this.assetManager = assetManager;
    }

    public void close() {
        n_close(mrubyInstancePointer);
    }

    public void loadString(String code) {
        n_loadString(mrubyInstancePointer, code);
    }

    public void setPrintCallback(PrintCallback printCallback) {
        this.printCallback = printCallback;
    }

    public void printStringCallback(String value) {
        if (printCallback != null) {
            printCallback.print(value);
        }
    }

    /*package*/ long getMRubyInstancePointer() {
        return mrubyInstancePointer;
    }

    private native long n_open();
    private native void n_close(long mrb);

    private native void n_loadString(long mrb, String code);

    public interface PrintCallback {
        void print(String value);
    }
}
