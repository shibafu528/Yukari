package info.shibafu528.yukari.exvoice;

/**
 * MRubyのProcをラップしてJavaから実行できるようにします。
 *
 * Created by shibafu on 2016/05/05.
 */
public class ProcWrapper {

    private long mRubyInstancePointer;
    private long rProcPointer;
    private boolean disposed;

    /*package*/ ProcWrapper(long mRubyInstancePointer, long rProcPointer) {
        this.mRubyInstancePointer = mRubyInstancePointer;
        this.rProcPointer = rProcPointer;
    }

    public Object exec(Object... args) {
        return execNative(this.mRubyInstancePointer, args);
    }

    public Object execWithContext(MRuby mRuby, Object... args) {
        return execNative(mRuby.getMRubyInstancePointer(), args);
    }

    public void dispose() {
        if (!disposed) {
            disposeNative(mRubyInstancePointer);
            rProcPointer = 0;
            disposed = true;
        }
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            super.finalize();
        } finally {
            dispose();
        }
    }

    private native void disposeNative(long mRubyInstancePointer);

    private native Object execNative(long mRubyInstancePointer, Object... args);
}
