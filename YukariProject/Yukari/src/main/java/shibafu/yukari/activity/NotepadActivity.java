package shibafu.yukari.activity;

import android.app.Activity;
import android.os.Bundle;

import shibafu.yukari.R;

/**
 * Created by shibafu on 14/10/05.
 */
public class NotepadActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notepad);
    }
}
