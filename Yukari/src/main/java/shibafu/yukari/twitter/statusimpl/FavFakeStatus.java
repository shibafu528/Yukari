package shibafu.yukari.twitter.statusimpl;

import twitter4j.User;

/**
 * Created by shibafu on 14/06/19.
 */
public class FavFakeStatus extends FakeStatus{

    private User user;
    private boolean favorited;

    public FavFakeStatus(long id, boolean favorited, User user) {
        super(id);
        this.favorited = favorited;
        this.user = user;
    }

    @Override
    public boolean isFavorited() {
        return favorited;
    }

    @Override
    public User getUser() {
        return user;
    }
}
