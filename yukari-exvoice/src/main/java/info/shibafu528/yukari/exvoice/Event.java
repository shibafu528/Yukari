package info.shibafu528.yukari.exvoice;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Created by shibafu on 2016/05/01.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Event {
    /**
     * バインド対象のPluggaloidイベント名
     * @return
     */
    String value() default "";
}
