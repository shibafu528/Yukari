package shibafu.yukari.common.span;

import android.content.Intent;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.text.style.ClickableSpan;
import android.view.View;
import shibafu.yukari.activity.ProfileActivity;
import shibafu.yukari.database.AuthUserRecord;

public class UserProfileSpan extends ClickableSpan {
    private AuthUserRecord userRecord;
    private String url;

    public UserProfileSpan(AuthUserRecord userRecord, String url) {
        this.userRecord = userRecord;
        this.url = url;
    }

    @Override
    public void onClick(@NonNull View widget) {
        Intent intent = ProfileActivity.newIntent(widget.getContext(), userRecord, Uri.parse(url));
        widget.getContext().startActivity(intent);
    }
}
