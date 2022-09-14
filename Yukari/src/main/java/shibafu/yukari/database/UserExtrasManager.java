package shibafu.yukari.database;

import androidx.annotation.Nullable;

import java.util.List;

public interface UserExtrasManager {
    void setColor(String url, int color);

    void setPriority(String url, AuthUserRecord userRecord);

    @Nullable
    AuthUserRecord getPriority(String url);

    List<UserExtras> getUserExtras();
}
