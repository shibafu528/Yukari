package info.shibafu528.yukari.exvoice;

/**
 * Created by shibafu on 2016/04/10.
 */
public final class BuildInfo {
    private BuildInfo() {}

    static {
        System.loadLibrary("exvoice");
    }

    /**
     * exvoiceのABIを取得します。
     * @return ABI
     */
    public static native String getABI();

    /**
     * exvoiceのビルド日時を取得します。
     * @return ビルド日時
     */
    public static native String getBuildDateTime();

    /**
     * ネイティブ定数 MRUBY_DESCRIPTION を取得します。
     * @return MRUBY_DESCRIPTION
     */
    public static native String getMRubyDescription();

    /**
     * ネイティブ定数 MRUBY_COPYRIGHT を取得します。
     * @return MRUBY_COPYRIGHT
     */
    public static native String getMRubyCopyright();
}
