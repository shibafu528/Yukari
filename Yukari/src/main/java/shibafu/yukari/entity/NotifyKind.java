package shibafu.yukari.entity;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@IntDef({NotifyHistory.KIND_FAVED, NotifyHistory.KIND_RETWEETED})
@Retention(RetentionPolicy.SOURCE)
public @interface NotifyKind {
}
