package info.shibafu528.yukari.processor.autorelease;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * {@link AutoReleaser#release(Object)}での自動解放の対象としてマークします。
 *
 * Created by shibafu on 2015/07/28.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.SOURCE)
public @interface AutoRelease {
}
