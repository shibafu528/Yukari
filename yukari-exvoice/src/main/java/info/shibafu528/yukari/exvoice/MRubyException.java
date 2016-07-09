package info.shibafu528.yukari.exvoice;

/**
 * MRuby上で例外が発生した際、それをラップして通知する例外です。
 *
 * Created by shibafu on 2016/07/09.
 */
public class MRubyException extends RuntimeException {
    public MRubyException(Throwable throwable) {
        super(throwable);
    }

    public MRubyException(String detailMessage, Throwable throwable) {
        super(detailMessage, throwable);
    }

    public MRubyException(String detailMessage) {
        super(detailMessage);
    }

    public MRubyException() {
        super();
    }
}
