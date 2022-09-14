package shibafu.yukari.database;

import androidx.annotation.Nullable;

import java.util.Collection;
import java.util.List;

public class UserExtrasManagerImpl implements UserExtrasManager {
    private final CentralDatabase database;
    private final List<UserExtras> userExtras;

    public UserExtrasManagerImpl(CentralDatabase database, List<AuthUserRecord> users) {
        this.database = database;
        this.userExtras = database.getRecords(UserExtras.class, new Class[]{Collection.class}, users);
    }

    @Override
    public void setColor(String url, int color) {
        UserExtras extras = null;
        for (UserExtras userExtra : userExtras) {
            if (userExtra.getId().equals(url)) {
                userExtra.setColor(color);
                extras = userExtra;
                break;
            }
        }
        if (extras == null) {
            extras = new UserExtras(url);
            extras.setColor(color);
            userExtras.add(extras);
        }
        database.updateRecord(extras);
    }

    @Override
    public void setPriority(String url, AuthUserRecord userRecord) {
        UserExtras extras = null;
        for (UserExtras userExtra : userExtras) {
            if (userExtra.getId().equals(url)) {
                userExtra.setPriorityAccount(userRecord);
                extras = userExtra;
                break;
            }
        }
        if (extras == null) {
            extras = new UserExtras(url);
            extras.setPriorityAccount(userRecord);
            userExtras.add(extras);
        }
        database.updateRecord(extras);
    }

    @Override
    @Nullable
    public AuthUserRecord getPriority(String url) {
        for (UserExtras userExtra : userExtras) {
            if (userExtra.getId().equals(url)) {
                return userExtra.getPriorityAccount();
            }
        }
        return null;
    }

    @Override
    public List<UserExtras> getUserExtras() {
        return userExtras;
    }
}
