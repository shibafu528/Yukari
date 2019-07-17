package shibafu.yukari.database;

import android.support.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@IntDef({Provider.API_SYSTEM, Provider.API_TWITTER, Provider.API_MASTODON})
@Retention(RetentionPolicy.SOURCE)
public @interface ApiType {
}
