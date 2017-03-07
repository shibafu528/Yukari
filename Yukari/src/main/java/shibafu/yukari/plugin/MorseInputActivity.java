package shibafu.yukari.plugin;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import shibafu.yukari.R;
import shibafu.yukari.util.MorseCodec;

/**
 * Created by Shibafu on 13/08/14.
 */
public class MorseInputActivity extends Activity {

    private AlertDialog currentDialog;

    private TextView tvPreview;
    private EditText etInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        switch (PreferenceManager.getDefaultSharedPreferences(this).getString("pref_theme", "light")) {
            default:
                setTheme(R.style.AppThemeTranslucent);
                break;
            case "dark":
                setTheme(R.style.AppThemeTranslucent_Dark);
                break;
        }
        super.onCreate(savedInstanceState);

        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        View v = inflater.inflate(R.layout.view_morse, null);

        tvPreview = (TextView) v.findViewById(R.id.tvMorsePreview);
        etInput = (EditText) v.findViewById(R.id.etMorseInput);
        etInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                tvPreview.setText(encode(s.toString()));
            }
        });
        etInput.setOnFocusChangeListener((v1, hasFocus) -> {
            if (hasFocus) {
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                imm.showSoftInput(etInput, InputMethodManager.SHOW_IMPLICIT);
            }
        });

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("モールス変換");
        builder.setView(v);
        builder.setPositiveButton("OK", (dialog, which) -> {
            String out = encode(etInput.getText().toString());
            Intent intent = new Intent();
            intent.putExtra("replace_key", out);
            setResult(RESULT_OK, intent);
            finish();
        });
        builder.setNegativeButton("キャンセル", (dialog, which) -> {
            dialog.dismiss();
            currentDialog = null;
            setResult(RESULT_CANCELED);
            finish();
        });
        builder.setOnCancelListener(dialog -> {
            dialog.dismiss();
            currentDialog = null;
            setResult(RESULT_CANCELED);
            finish();
        });

        currentDialog = builder.create();
        currentDialog.show();

        this.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (currentDialog != null) {
            currentDialog.show();
        }
        else {
            setResult(RESULT_CANCELED);
            finish();
        }
    }

    private String encode(String str) {
        String encoded = MorseCodec.encode(str);
        encoded = encoded.replace((char)0x2212, (char)0xff0d);
        return encoded;
    }
}
