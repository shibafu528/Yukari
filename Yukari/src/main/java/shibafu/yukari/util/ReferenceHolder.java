package shibafu.yukari.util;

import lombok.Data;

/**
 * 参照を保持するためのホルダークラスです。
 *
 * Created by shibafu on 2015/12/22.
 */
@Data
public class ReferenceHolder<T> {
    private T reference;
}
