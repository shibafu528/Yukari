package info.shibafu528.yukari.processor.messagequeue;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * メッセージキューを自動生成します。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface MessageQueue {
    String queueClass() default "";
}
