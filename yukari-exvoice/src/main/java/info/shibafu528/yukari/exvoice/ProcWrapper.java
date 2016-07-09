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

    /**
     * ブロックをMRuby上で評価します。
     * @param args ブロックに渡す引数
     * @return ブロックの返り値
     * @exception MRubyException MRuby上で例外が発生した場合、この例外でラップされます。
     */
    public Object exec(Object... args) {
        return execNative(this.mRubyInstancePointer, args);
    }

    /**
     * MRubyのインスタンスを明示的に指定し、ブロックをMRuby上で評価します。
     * @param mRuby {@link MRuby} のインスタンス
     * @param args ブロックに渡す引数
     * @return ブロックの返り値
     * @exception MRubyException MRuby上で例外が発生した場合、この例外でラップされます。
     */
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
