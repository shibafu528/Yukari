package shibafu.yukari.common;

/**
 * Created by shibafu on 14/07/12.
 */
public class Versionize<T> {
    private int version;
    private T content;

    public Versionize(int version, T content) {
        this.version = version;
        this.content = content;
    }

    public int getVersion() {
        return version;
    }

    public T getContent() {
        return content;
    }
}
