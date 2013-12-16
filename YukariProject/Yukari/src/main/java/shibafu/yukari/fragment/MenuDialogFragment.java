package shibafu.yukari.fragment;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.WindowManager;

import shibafu.yukari.R;

/**
 * Created by Shibafu on 13/12/16.
 */
public class MenuDialogFragment extends DialogFragment {

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        View v = getActivity().getLayoutInflater().inflate(R.layout.dialog_menu, null);

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setView(v);

        AlertDialog dialog = builder.create();

        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int dialogWidth = (int) (0.95f * metrics.widthPixels);
        WindowManager.LayoutParams lp = dialog.getWindow().getAttributes();
        lp.width = dialogWidth;
        dialog.getWindow().setAttributes(lp);

        return dialog;
    }
}
