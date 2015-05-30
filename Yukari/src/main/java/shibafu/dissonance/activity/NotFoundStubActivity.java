package shibafu.dissonance.activity;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;

/**
 * Created by Shibafu on 13/08/13.
 */
public class NotFoundStubActivity extends Activity{
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showDialog(0);
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        return new AlertDialog.Builder(this)
                .setTitle("Yukari : 未実装の操作")
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setMessage("この機能はまだ実装されていません。時が来るまではこの機能を忘れて気長にお待ちください。\n\nDebug Info:\ngetDataString() = " + getIntent().getDataString())
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        finish();
                    }
                })
                .create();
    }
}
