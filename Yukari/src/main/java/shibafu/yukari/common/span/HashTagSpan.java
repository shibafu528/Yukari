package shibafu.yukari.common.span;

import android.content.Intent;
import androidx.annotation.NonNull;
import android.text.style.ClickableSpan;
import android.view.View;
import shibafu.yukari.activity.MainActivity;

public class HashTagSpan extends ClickableSpan {
    private String tag;

    public HashTagSpan(String tag) {
        if (tag.startsWith("#")) {
            this.tag = tag;
        } else {
            this.tag = "#" + tag;
        }
    }

    @Override
    public void onClick(@NonNull View widget) {
        Intent intent = new Intent(widget.getContext(), MainActivity.class);
        intent.putExtra(MainActivity.EXTRA_SEARCH_WORD, tag);
        widget.getContext().startActivity(intent);
    }
}
