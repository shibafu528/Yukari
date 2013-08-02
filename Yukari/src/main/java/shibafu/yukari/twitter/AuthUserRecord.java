package shibafu.yukari.twitter;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import twitter4j.auth.AccessToken;

public class AuthUserRecord implements Serializable{
	private static final long serialVersionUID = 1L;

	public long NumericId;
	public String ScreenName;
	public AccessToken Token;

    public AuthUserRecord(AccessToken token) {
        Token = token;
        NumericId = token.getUserId();
        ScreenName = token.getScreenName();
    }

    public AccessToken getAccessToken() {
		return Token;
	}

    @Override
    public boolean equals(Object o) {
        if (o == this)
            return true;
        if (o == null)
            return false;
        if (o.getClass() != this.getClass())
            return false;
        if (((AuthUserRecord)o).ScreenName.equals(ScreenName))
            return true;
        else
            return false;
    }

    @Override
    public int hashCode() {
        return Integer.decode(ScreenName);
    }

    public List<AuthUserRecord> toSingleList() {
        List<AuthUserRecord> l = new ArrayList<AuthUserRecord>();
        l.add(this);
        return l;
    }
}
