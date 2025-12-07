package shibafu.yukari.activity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;

import shibafu.yukari.activity.base.ActionBarYukariBase;
import shibafu.yukari.core.App;
import shibafu.yukari.databinding.ActivityQueryBinding;
import shibafu.yukari.entity.MockStatus;
import shibafu.yukari.filter.FilterQuery;
import shibafu.yukari.filter.compiler.FilterCompilerException;
import shibafu.yukari.filter.compiler.QueryCompiler;
import shibafu.yukari.filter.compiler.TokenizeException;
import shibafu.yukari.database.AuthUserRecord;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Created by shibafu on 2015/08/18.
 */
public class QueryEditorActivity extends ActionBarYukariBase {
    private ActivityQueryBinding binding;
    private Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityQueryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (getIntent().hasExtra(Intent.EXTRA_TEXT)) {
            binding.etQuery.setText(getIntent().getStringExtra(Intent.EXTRA_TEXT));
        }

        binding.etQuery.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                handler.removeCallbacksAndMessages(null);
                handler.postDelayed(() -> {
                    try {
                        List<AuthUserRecord> userRecords = App.getInstance(getApplicationContext()).getAccountManager().getUsers();
                        FilterQuery q = QueryCompiler.compile(userRecords, s.toString());
                        boolean result = q.evaluate(new MockStatus(0, userRecords.get(0)), new ArrayList<>(), new HashMap<>());
                        binding.tvStatus.setText("OK. => " + result);
                    } catch (FilterCompilerException | TokenizeException e) {
                        binding.tvStatus.setText(e.toString());
                    }
                }, 1500);
            }
        });

        binding.btnDone.setOnClickListener(this::onClickDone);
        binding.btnLeftParen.setOnClickListener(this::onClickLeftParen);
        binding.btnRightParen.setOnClickListener(this::onClickRightParen);
    }

    void onClickDone(View v) {
        Intent data = new Intent();
        data.putExtras(getIntent());
        data.putExtra(Intent.EXTRA_TEXT, binding.etQuery.getText().toString());
        setResult(RESULT_OK, data);
        finish();
    }

    void onClickLeftParen(View v) {
        appendTextInto("(");
    }

    void onClickRightParen(View v) {
        appendTextInto(")");
    }

    private void appendTextInto(String text) {
        int start = binding.etQuery.getSelectionStart();
        int end = binding.etQuery.getSelectionEnd();
        binding.etQuery.getText().replace(Math.min(start, end), Math.max(start, end), text);
    }
}
