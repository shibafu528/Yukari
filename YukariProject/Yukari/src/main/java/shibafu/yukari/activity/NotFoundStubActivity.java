package shibafu.yukari.activity;

import android.R;
import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

/**
 * Created by Shibafu on 13/08/13.
 */
public class NotFoundStubActivity extends Activity{
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTitle("Yukari : 未実装の操作");

        TextView tv = new TextView(this);
        tv.setText("この機能はまだ実装されていません。時が来るまではこの機能を忘れて気長にお待ちください。\n\nDebug Info:\ngetDataString() = " + getIntent().getDataString());

        setContentView(tv);
    }
}
