package shibafu.yukari.twitter.statusimpl;

import twitter4j.User;

/**
 * Created by shiba on 2016/02/19.
 */
public class ExceptionStatus extends FakeStatus {

    private final Exception exception;
    private final User user = new FakeUser() {
        @Override
        public String getScreenName() {
            return "ERROR";
        }

        @Override
        public String getName() {
            return exception.getClass().getSimpleName();
        }
    };

    public ExceptionStatus(long id, Exception exception) {
        super(id);
        this.exception = exception;
    }

    @Override
    public String getText() {
        return exception.getMessage();
    }

    @Override
    public User getUser() {
        return user;
    }

    @Override
    public String getSource() {
        return "System";
    }
}
