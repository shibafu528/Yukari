package shibafu.dissonance.fragment;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;

/**
* Created by shibafu on 14/08/05.
*/
public class PostProgressDialogFragment extends DialogFragment {
    public static PostProgressDialogFragment newInstance() {
        PostProgressDialogFragment fragment = new PostProgressDialogFragment();
        fragment.setCancelable(false);
        return fragment;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        ProgressDialog pd = new ProgressDialog(getActivity());
        pd.setMessage("送信中...");
        pd.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        pd.setIndeterminate(true);
        return pd;
    }
}
