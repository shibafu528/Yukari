package shibafu.yukari.twitter.statusimpl;

/**
 * Created by shibafu on 14/06/19.
 */
public class FavFakeStatus extends FakeStatus{

    private boolean favorited;

    public FavFakeStatus(long id, boolean favorited) {
        super(id);
        this.favorited = favorited;
    }

    @Override
    public boolean isFavorited() {
        return favorited;
    }
}
