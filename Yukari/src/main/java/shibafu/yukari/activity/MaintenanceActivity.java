package shibafu.yukari.activity;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.os.Bundle;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.fragment.app.DialogFragment;

import shibafu.yukari.R;
import shibafu.yukari.activity.base.ActionBarYukariBase;
import shibafu.yukari.common.async.SimpleAsyncTask;
import shibafu.yukari.core.App;
import shibafu.yukari.database.CentralDatabase;
import shibafu.yukari.databinding.FragmentDbmtBinding;
import shibafu.yukari.fragment.base.YukariBaseFragment;

/**
 * Created by shibafu on 14/07/05.
 */
public class MaintenanceActivity extends ActionBarYukariBase {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_parent);

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.frame, new DBMaintenanceFragment())
                    .commit();
        }
    }

    @Override
    public void onServiceConnected() {}

    @Override
    public void onServiceDisconnected() {}

    public static class DBMaintenanceFragment extends YukariBaseFragment {
        private FragmentDbmtBinding binding;

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            binding = FragmentDbmtBinding.inflate(inflater, container, false);

            binding.tvYdbName.setText(String.format("%s, version %d", CentralDatabase.DB_FILENAME, CentralDatabase.DB_VER));
            binding.tvYdbSize.setText(Formatter.formatFileSize(getActivity(), getActivity().getDatabasePath(CentralDatabase.DB_FILENAME).length()));
            binding.btnYdbWipe.setOnClickListener(this::onClickWipe);
            binding.btnYdbVacuum.setOnClickListener(this::onClickVacuum);

            return binding.getRoot();
        }

        @Override
        public void onDestroyView() {
            super.onDestroyView();
            binding = null;
        }

        public void onClickWipe(View v) {
            new SimpleAsyncTask() {

                private SimpleProgressDialogFragment fragment;

                @Override
                protected Void doInBackground(Void... params) {
                    App.getInstance(requireActivity()).getDatabase().wipeUsers();
                    return null;
                }

                @Override
                protected void onPreExecute() {
                    super.onPreExecute();
                    fragment = SimpleProgressDialogFragment.newInstance(
                            null, "Wipe User table...", true, false
                    );
                    fragment.show(getFragmentManager(), "wipe");
                }

                @Override
                protected void onPostExecute(Void aVoid) {
                    super.onPostExecute(aVoid);
                    fragment.dismiss();
                    onServiceConnected();
                }
            }.executeParallel();
        }

        public void onClickVacuum(View v) {
            new SimpleAsyncTask() {

                private SimpleProgressDialogFragment fragment;

                @Override
                protected Void doInBackground(Void... params) {
                    App.getInstance(requireContext()).getDatabase().vacuum();
                    return null;
                }

                @Override
                protected void onPreExecute() {
                    super.onPreExecute();
                    fragment = SimpleProgressDialogFragment.newInstance(
                            null, "Vacuum database...", true, false
                    );
                    fragment.show(getFragmentManager(), "vacuum");
                }

                @Override
                protected void onPostExecute(Void aVoid) {
                    super.onPostExecute(aVoid);
                    fragment.dismiss();
                    reload();
                }
            }.executeParallel();
        }

        @Override
        public void onResume() {
            super.onResume();
            reload();
        }

        @Override
        public void onServiceConnected() {
            reload();
        }

        @Override
        public void onServiceDisconnected() {}

        private void reload() {
            binding.tvYdbUserEnt.setText(String.format("%d entries", App.getInstance(requireContext()).getDatabase().getUsersCursor().getCount()));
            binding.tvYdbSize.setText(Formatter.formatFileSize(getActivity(), getActivity().getDatabasePath(CentralDatabase.DB_FILENAME).length()));
        }
    }

    public static class SimpleProgressDialogFragment extends DialogFragment {

        public static SimpleProgressDialogFragment newInstance(String title, String message, boolean indeterminate, boolean isCancellable) {
            SimpleProgressDialogFragment fragment = new SimpleProgressDialogFragment();
            Bundle args = new Bundle();
            args.putString("title", title);
            args.putString("message", message);
            args.putBoolean("indeterminate", indeterminate);
            fragment.setArguments(args);
            fragment.setCancelable(isCancellable);
            return fragment;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            Bundle args = getArguments();
            return ProgressDialog.show(getActivity(),
                    args.getString("title"),
                    args.getString("message"),
                    args.getBoolean("indeterminate"));
        }
    }
}
