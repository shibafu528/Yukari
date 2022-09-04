package info.shibafu528.yukari.processor.messagequeue;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * {@link MessageQueue} によるキュー処理の対象外としてマークし、直接ハンドラーのメソッドが呼び出されるようにします。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.SOURCE)
public @interface PassThrough {
}
