package info.shibafu528.yukari.processor.autorelease;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * {@link AutoRelease} 用のユーティリティ
 */
public class AutoReleaser {

    /**
     * {@link AutoRelease} アノテーションの付いている全てのフィールドにnullを設定します。
     * @param target 対象のインスタンス
     */
    public static void release(Object target) {
        String autoReleaseClassName = target.getClass().getCanonicalName() + "$AutoRelease";
        try {
            Class<?> autoReleaseClass = Class.forName(autoReleaseClassName);
            Method releaseMethod = autoReleaseClass.getMethod("release", target.getClass());
            releaseMethod.invoke(null, target);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(autoReleaseClassName + " が見つかりませんでした。ビルドが壊れているか、AutoReleaseを使っていないクラスじゃない？", e);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(autoReleaseClassName + ".release が見つかりませんでした。ライブラリ壊れてない？", e);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }
}
