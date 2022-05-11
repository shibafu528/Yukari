package shibafu.yukari.fragment

import android.app.Dialog
import android.app.ProgressDialog
import android.content.DialogInterface
import android.os.Bundle
import androidx.fragment.app.DialogFragment

class SimpleProgressDialogFragment : DialogFragment() {
    private var requestCode: Int = -1
    private var dismissRequest: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val args = arguments ?: Bundle.EMPTY
        requestCode = args.getInt(ARG_REQUEST_CODE)
    }

    @Suppress("DEPRECATION")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val pd = ProgressDialog(activity)
        pd.setMessage("読み込み中...")
        pd.setProgressStyle(ProgressDialog.STYLE_SPINNER)
        pd.isIndeterminate = true
        return pd
    }

    override fun onResume() {
        super.onResume()

        if (dismissRequest) {
            dismiss()
        }
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)

        val targetFragment = targetFragment
        if (targetFragment is OnCancelListener) {
            targetFragment.onProgressDialogCancel(requestCode, dialog)
        }
    }

    override fun dismiss() {
        if (isResumed) {
            super.dismiss()
        } else {
            dismissRequest = true
        }
    }

    interface OnCancelListener {
        fun onProgressDialogCancel(requestCode: Int, dialog: DialogInterface?)
    }

    class Builder(private val requestCode: Int) {
        fun build(): SimpleProgressDialogFragment {
            val fragment = SimpleProgressDialogFragment()
            fragment.arguments = Bundle().apply {
                putInt(ARG_REQUEST_CODE, requestCode)
            }
            return fragment
        }
    }

    companion object {
        private const val ARG_REQUEST_CODE = "requestCode"
    }
}