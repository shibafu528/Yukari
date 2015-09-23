package shibafu.yukari.activity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.TextView;
import butterknife.ButterKnife;
import butterknife.InjectView;
import butterknife.OnClick;
import shibafu.yukari.R;
import shibafu.yukari.activity.base.ActionBarYukariBase;
import shibafu.yukari.filter.FilterQuery;
import shibafu.yukari.filter.compiler.FilterCompilerException;
import shibafu.yukari.filter.compiler.QueryCompiler;
import shibafu.yukari.twitter.statusimpl.FakeStatus;

import java.util.ArrayList;

/**
 * Created by shibafu on 2015/08/18.
 */
public class QueryEditorActivity extends ActionBarYukariBase {

    @InjectView(R.id.etQuery)
    EditText query;

    @InjectView(R.id.tvStatus)
    TextView compileStatus;

    private Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_query);
        ButterKnife.inject(this);

        query.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                handler.removeCallbacksAndMessages(null);
                handler.postDelayed(() -> {
                    try {
                        FilterQuery q = QueryCompiler.compile(new ArrayList<>(), s.toString());
                        compileStatus.setText("OK. => " + q.evaluate(new FakeStatus(0), new ArrayList<>()));
                    } catch (FilterCompilerException e) {
                        compileStatus.setText(e.toString());
                    }
                }, 1500);
            }
        });
    }

    @OnClick(R.id.btnDone)
    void onClickDone() {
        Intent data = new Intent();
        data.putExtra(Intent.EXTRA_TEXT, query.getText().toString());
        setResult(RESULT_OK, data);
        finish();
    }

    @OnClick(R.id.btnLeftParen)
    void onClickLeftParen() {
        appendTextInto("(");
    }

    @OnClick(R.id.btnRightParen)
    void onClickRightParen() {
        appendTextInto(")");
    }

    private void appendTextInto(String text) {
        int start = query.getSelectionStart();
        int end = query.getSelectionEnd();
        query.getText().replace(Math.min(start, end), Math.max(start, end), text);
    }

    @Override
    public void onServiceConnected() {}

    @Override
    public void onServiceDisconnected() {}
}
