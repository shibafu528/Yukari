package shibafu.yukari.fragment;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import shibafu.yukari.R;

/**
 * Created by shibafu on 14/02/13.
 */
public class SearchDialogFragment extends DialogFragment{
    public interface SearchDialogCallback {
        void onSearchQuery(String searchQuery, boolean isSavedSearch, boolean useTracking);
    }

    private EditText searchQuery;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dialog = new Dialog(getActivity());

        dialog.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        dialog.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
        dialog.getWindow().setBackgroundDrawableResource(R.drawable.dialog_full_holo_light);

        dialog.setContentView(inflateView(getActivity().getLayoutInflater()));

        return dialog;
    }

    private View inflateView(LayoutInflater inflater) {
        View v = inflater.inflate(R.layout.dialog_search, null);

        searchQuery = (EditText) v.findViewById(R.id.editText);
        searchQuery.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View view, int i, KeyEvent keyEvent) {
                if (keyEvent.getAction() == KeyEvent.ACTION_DOWN &&
                        keyEvent.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                    InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                    sendQuery();
                }
                return false;
            }
        });

        ImageButton ibSearch = (ImageButton) v.findViewById(R.id.ibSearch);
        ibSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendQuery();
            }
        });
        ibSearch.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                searchQuery.setText(String.format("\"%s\"", searchQuery.getText().toString()));
                sendQuery();
                return true;
            }
        });

        return v;
    }

    private void sendQuery() {
        if (searchQuery.getText().length() < 1) {
            Toast.makeText(getActivity(), "検索ワードが空です", Toast.LENGTH_LONG).show();
        }
        else {
            String s = searchQuery.getText().toString();
            ((SearchDialogCallback)getActivity()).onSearchQuery(s, false, false);
            dismiss();
        }
    }
}
