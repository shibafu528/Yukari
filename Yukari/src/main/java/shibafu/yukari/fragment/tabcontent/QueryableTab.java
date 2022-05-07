package shibafu.yukari.fragment.tabcontent;

import androidx.annotation.NonNull;
import shibafu.yukari.entity.Status;

import java.util.Collection;

/**
 * クエリによる {@link Status} の抽出をサポートするため、検索対象のコレクションを提供するインターフェース。
 */
public interface QueryableTab {
    @NonNull
    Collection<Status> getQueryableElements();
}
