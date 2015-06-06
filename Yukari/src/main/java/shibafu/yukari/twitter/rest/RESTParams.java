package shibafu.yukari.twitter.rest;

import shibafu.yukari.twitter.AuthUserRecord;
import twitter4j.Paging;

/**
 * Created by shibafu on 15/06/06.
 */
public class RESTParams {
    private Paging paging;
    private AuthUserRecord userRecord;
    private boolean saveLastPaging;

    public RESTParams(AuthUserRecord userRecord) {
        this.paging = new Paging();
        this.userRecord = userRecord;
    }

    public RESTParams(long lastStatusId, AuthUserRecord userRecord) {
        this.paging = new Paging();
        if (lastStatusId > -1) {
            paging.setMaxId(lastStatusId - 1);
        }
        this.userRecord = userRecord;
    }

    public RESTParams(AuthUserRecord userRecord, boolean saveLastPaging) {
        this.paging = new Paging();
        this.userRecord = userRecord;
        this.saveLastPaging = saveLastPaging;
    }

    public Paging getPaging() {
        return paging;
    }

    public AuthUserRecord getUserRecord() {
        return userRecord;
    }

    public boolean isSaveLastPaging() {
        return saveLastPaging;
    }
}
