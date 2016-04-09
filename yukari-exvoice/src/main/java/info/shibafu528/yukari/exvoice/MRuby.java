package info.shibafu528.yukari.exvoice;

import android.util.Log;

/**
 * Created by shibafu on 2016/03/28.
 */
public class MRuby {
    private long mrubyInstance;
    private PrintCallback printCallback;

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

    public void setPrintCallback(PrintCallback printCallback) {
        this.printCallback = printCallback;
    }

    public void printStringCallback(String value) {
        Log.d("exvoice-j", "printStringCallback: " + value);
        if (printCallback != null) {
            printCallback.print(value);
        }
    }

    private native long n_open();
    private native void n_close(long mrb);

    private native void n_loadString(long mrb, String code);

    public interface PrintCallback {
        void print(String value);
    }
}
