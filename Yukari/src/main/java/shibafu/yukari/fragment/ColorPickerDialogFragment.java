package shibafu.yukari.fragment;

import android.support.v7.app.AlertDialog;
import android.app.Dialog;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.View;
import com.larswerkman.holocolorpicker.ColorPicker;
import com.larswerkman.holocolorpicker.OpacityBar;
import com.larswerkman.holocolorpicker.SVBar;
import shibafu.yukari.R;

/**
 * Created by shibafu on 14/09/06.
 */
public class ColorPickerDialogFragment extends DialogFragment{
    private static final String ARG_COLOR = "color";
    private static final String ARG_TAG = "tag";

    private String tag;

    public interface ColorPickerCallback {
        void onColorPicked(int color, String tag);
    }

    public static ColorPickerDialogFragment newInstance(int defaultColor, String tag) {
        ColorPickerDialogFragment fragment = new ColorPickerDialogFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_COLOR, defaultColor);
        args.putString(ARG_TAG, tag);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Bundle args = getArguments();
        tag = args.getString(ARG_TAG);
        int color = args.getInt(ARG_COLOR);
        if (color == 0) {
            color = Color.RED;
        }

        View v = getActivity().getLayoutInflater().inflate(R.layout.dialog_color, null);
        final ColorPicker picker = (ColorPicker) v.findViewById(R.id.picker);
        SVBar svBar = (SVBar) v.findViewById(R.id.svbar);
        OpacityBar opacityBar = (OpacityBar) v.findViewById(R.id.opacitybar);
        picker.addSVBar(svBar);
        picker.addOpacityBar(opacityBar);
        picker.setColor(color);
        picker.setShowOldCenterColor(false);

        return new AlertDialog.Builder(getActivity())
                .setTitle("色を選択")
                .setPositiveButton("OK", (dialog, which) -> {
                    ColorPickerCallback callback = null;
                    if (getTargetFragment() != null && getTargetFragment() instanceof ColorPickerCallback) {
                        callback = (ColorPickerCallback) getTargetFragment();
                    } else if (getActivity() instanceof ColorPickerCallback) {
                        callback = (ColorPickerCallback) getActivity();
                    }

                    if (callback != null) {
                        callback.onColorPicked(picker.getColor(), tag);
                    }
                })
                .setNegativeButton("キャンセル", null)
                .setView(v)
                .create();
    }
}
