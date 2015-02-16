package shibafu.yukari.fragment;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;

/**
 * Created by shibafu on 14/02/18.
 */
public class SimpleAlertDialogFragment extends DialogFragment implements DialogInterface.OnClickListener {

    public static final String ARG_REQUEST_CODE = "requestcode";
    public static final String ARG_ICON = "icon";
    public static final String ARG_TITLE = "title";
    public static final String ARG_MESSAGE = "message";
    public static final String ARG_POSITIVE = "positive";
    public static final String ARG_NEGATIVE = "negative";

    public static interface OnDialogChoseListener {
        void onDialogChose(int requestCode, int which);
    }

    @Deprecated
    public static SimpleAlertDialogFragment newInstance(
            String title, String message, String positive, String negative) {
        SimpleAlertDialogFragment fragment = new SimpleAlertDialogFragment();
        Bundle args = new Bundle();
        args.putBoolean("compat", true);
        args.putString(ARG_TITLE, title);
        args.putString(ARG_MESSAGE, message);
        args.putString(ARG_POSITIVE, positive);
        args.putString(ARG_NEGATIVE, negative);
        fragment.setArguments(args);
        return fragment;
    }

    public static SimpleAlertDialogFragment newInstance(
            int requestCode,
            String title, String message, String positive, String negative) {
        SimpleAlertDialogFragment fragment = new SimpleAlertDialogFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_REQUEST_CODE, requestCode);
        args.putString(ARG_TITLE, title);
        args.putString(ARG_MESSAGE, message);
        args.putString(ARG_POSITIVE, positive);
        args.putString(ARG_NEGATIVE, negative);
        fragment.setArguments(args);
        return fragment;
    }

    public static SimpleAlertDialogFragment newInstance(
            int requestCode,
            int iconId,
            String title, String message, String positive, String negative) {
        SimpleAlertDialogFragment fragment = new SimpleAlertDialogFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_REQUEST_CODE, requestCode);
        args.putInt(ARG_ICON, iconId);
        args.putString(ARG_TITLE, title);
        args.putString(ARG_MESSAGE, message);
        args.putString(ARG_POSITIVE, positive);
        args.putString(ARG_NEGATIVE, negative);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Bundle args = getArguments();
        int iconId = args.getInt(ARG_ICON, -1);
        String title = args.getString(ARG_TITLE);
        String message = args.getString(ARG_MESSAGE);
        String positive = args.getString(ARG_POSITIVE);
        String negative = args.getString(ARG_NEGATIVE);

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        if (iconId > -1) builder.setIcon(iconId);
        if (title != null) builder.setTitle(title);
        if (message != null) builder.setMessage(message);
        if (positive != null) builder.setPositiveButton(positive, this);
        if (negative != null) builder.setNegativeButton(negative, this);

        return builder.create();
    }

    @Override
    public void onClick(DialogInterface dialogInterface, int i) {
        dismiss();

        if (getArguments().getBoolean("compat")) {
            DialogInterface.OnClickListener listener = null;
            if (getParentFragment() != null &&
                    getParentFragment() instanceof DialogInterface.OnClickListener) {
                listener = (DialogInterface.OnClickListener) getParentFragment();
            } else if (getTargetFragment() != null &&
                    getTargetFragment() instanceof DialogInterface.OnClickListener) {
                listener = (DialogInterface.OnClickListener) getTargetFragment();
            } else if (getActivity() != null && getActivity() instanceof DialogInterface.OnClickListener) {
                listener = (DialogInterface.OnClickListener) getActivity();
            }

            if (listener != null) {
                listener.onClick(dialogInterface, i);
            }
        } else {
            OnDialogChoseListener listener = null;
            if (getParentFragment() != null &&
                    getParentFragment() instanceof OnDialogChoseListener) {
                listener = (OnDialogChoseListener) getParentFragment();
            } else if (getTargetFragment() != null &&
                    getTargetFragment() instanceof OnDialogChoseListener) {
                listener = (OnDialogChoseListener) getTargetFragment();
            } else if (getActivity() != null && getActivity() instanceof OnDialogChoseListener) {
                listener = (OnDialogChoseListener) getActivity();
            }

            if (listener != null) {
                listener.onDialogChose(getArguments().getInt(ARG_REQUEST_CODE), i);
            }
        }
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        onClick(dialog, DialogInterface.BUTTON_NEGATIVE);
    }
}
