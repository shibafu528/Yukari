package shibafu.yukari.entity;

import android.support.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * あるメッセージと自分との関係性
 * @see shibafu.yukari.entity.Status
 */
@IntDef({Status.RELATION_NONE, Status.RELATION_OWNED, Status.RELATION_MENTIONED_TO_ME})
@Retention(RetentionPolicy.SOURCE)
public @interface StatusRelation {
}
