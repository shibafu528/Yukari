package shibafu.yukari.fragment

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.support.v7.app.AlertDialog
import android.widget.Button

class SimpleAlertDialogFragment : DialogFragment(), DialogInterface.OnClickListener {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val args = arguments
        val iconId = args.getInt(ARG_ICON, -1)
        val title = args.getString(ARG_TITLE)
        val message = args.getString(ARG_MESSAGE)
        val positive = args.getString(ARG_POSITIVE)
        val neutral = args.getString(ARG_NEUTRAL)
        val negative = args.getString(ARG_NEGATIVE)
        val disableCaps = args.getBoolean(ARG_DISABLE_CAPS)

        val builder = AlertDialog.Builder(activity)

        if (iconId > -1) builder.setIcon(iconId)
        if (title != null) builder.setTitle(title)
        if (message != null) builder.setMessage(message)
        if (positive != null) builder.setPositiveButton(positive, this)
        if (neutral != null) builder.setNeutralButton(neutral, this)
        if (negative != null) builder.setNegativeButton(negative, this)

        val dialog = builder.create()

        // 大文字化対策
        if (disableCaps) {
            dialog.setOnShowListener { _ ->
                val views: Array<Button?> = arrayOf(dialog.findViewById(android.R.id.button1),
                        dialog.findViewById(android.R.id.button2),
                        dialog.findViewById(android.R.id.button3))
                for (v in views) {
                    if (v is Button) {
                        v.transformationMethod = null
                    }
                }
            }
        }

        return dialog
    }

    override fun onClick(dialogInterface: DialogInterface?, i: Int) {
        dismiss()

        var listener: OnDialogChoseListener? = null
        if (parentFragment is OnDialogChoseListener) {
            listener = parentFragment as OnDialogChoseListener
        } else if (targetFragment is OnDialogChoseListener) {
            listener = targetFragment as OnDialogChoseListener
        } else if (activity is OnDialogChoseListener) {
            listener = activity as OnDialogChoseListener
        }

        if (listener != null) {
            listener.onDialogChose(arguments.getInt(ARG_REQUEST_CODE), i, arguments.getBundle(ARG_EXTRAS))
        }
    }

    override fun onCancel(dialog: DialogInterface?) {
        onClick(dialog, DialogInterface.BUTTON_NEGATIVE)
    }

    interface OnDialogChoseListener {
        fun onDialogChose(requestCode: Int, which: Int, extras: Bundle?)
    }

    class Builder(private val requestCode: Int) {
        private var iconId: Int = 0
        private var title: String? = null
        private var message: String? = null
        private var positive: String? = null
        private var neutral: String? = null
        private var negative: String? = null
        private var disableCaps: Boolean = false
        private var extras: Bundle? = null

        fun setIconId(value: Int) = apply { iconId = value }
        fun setTitle(value: String) = apply { title = value }
        fun setMessage(value: String) = apply { message = value }
        fun setPositive(value: String) = apply { positive = value }
        fun setNeutral(value: String) = apply { neutral = value }
        fun setNegative(value: String) = apply { negative = value }
        fun setDisableCaps(value: Boolean) = apply { disableCaps = value }
        fun setExtras(value: Bundle) = apply { extras = value }

        fun build(): SimpleAlertDialogFragment {
            val fragment = SimpleAlertDialogFragment()
            fragment.arguments = Bundle().apply {
                putInt(ARG_REQUEST_CODE, requestCode)
                putInt(ARG_ICON, iconId)
                putString(ARG_TITLE, title)
                putString(ARG_MESSAGE, message)
                putString(ARG_POSITIVE, positive)
                putString(ARG_NEUTRAL, neutral)
                putString(ARG_NEGATIVE, negative)
                putBoolean(ARG_DISABLE_CAPS, disableCaps)
                putBundle(ARG_EXTRAS, extras)
            }
            return fragment
        }
    }

    companion object {
        const val ARG_REQUEST_CODE = "requestcode"
        const val ARG_ICON = "icon"
        const val ARG_TITLE = "title"
        const val ARG_MESSAGE = "message"
        const val ARG_POSITIVE = "positive"
        const val ARG_NEUTRAL = "neutral"
        const val ARG_NEGATIVE = "negative"
        const val ARG_DISABLE_CAPS = "disable_caps"
        const val ARG_EXTRAS = "extras"

        @JvmStatic
        fun newInstance(requestCode: Int, title: String?, message: String?, positive: String, negative: String?): SimpleAlertDialogFragment {
            val fragment = SimpleAlertDialogFragment()
            fragment.arguments = Bundle().apply {
                putInt(ARG_REQUEST_CODE, requestCode)
                putString(ARG_TITLE, title)
                putString(ARG_MESSAGE, message)
                putString(ARG_POSITIVE, positive)
                putString(ARG_NEGATIVE, negative)
            }
            return fragment
        }

        @JvmStatic
        fun newInstance(requestCode: Int, title: String?, message: String?, positive: String, neutral: String?, negative: String?): SimpleAlertDialogFragment {
            val fragment = SimpleAlertDialogFragment()
            fragment.arguments = Bundle().apply {
                putInt(ARG_REQUEST_CODE, requestCode)
                putString(ARG_TITLE, title)
                putString(ARG_MESSAGE, message)
                putString(ARG_POSITIVE, positive)
                putString(ARG_NEUTRAL, neutral)
                putString(ARG_NEGATIVE, negative)
            }
            return fragment
        }

        @JvmStatic
        fun newInstance(requestCode: Int, iconId: Int, title: String?, message: String?, positive: String, negative: String?): SimpleAlertDialogFragment {
            val fragment = SimpleAlertDialogFragment()
            fragment.arguments = Bundle().apply {
                putInt(ARG_REQUEST_CODE, requestCode)
                putInt(ARG_ICON, iconId)
                putString(ARG_TITLE, title)
                putString(ARG_MESSAGE, message)
                putString(ARG_POSITIVE, positive)
                putString(ARG_NEGATIVE, negative)
            }
            return fragment
        }
    }
}