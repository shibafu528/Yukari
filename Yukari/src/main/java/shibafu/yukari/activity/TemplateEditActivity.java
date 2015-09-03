package shibafu.yukari.activity;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.ListFragment;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import java.util.List;

import shibafu.yukari.R;
import shibafu.yukari.activity.base.ActionBarYukariBase;
import shibafu.yukari.database.CentralDatabase;
import shibafu.yukari.database.Template;
import shibafu.yukari.fragment.SimpleAlertDialogFragment;

/**
 * Created by shibafu on 15/06/23.
 */
public class TemplateEditActivity extends ActionBarYukariBase {
    private static final String FRAGMENT_TAG = "inner";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_parent);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.frame, new InnerFragment(), FRAGMENT_TAG)
                    .commit();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private InnerFragment findInnerFragment() {
        return ((InnerFragment) getSupportFragmentManager().findFragmentByTag(FRAGMENT_TAG));
    }

    @Override
    public void onServiceConnected() {
        findInnerFragment().reloadList();
    }

    @Override
    public void onServiceDisconnected() {}

    public static class InnerFragment extends ListFragment implements SimpleAlertDialogFragment.OnDialogChoseListener {
        private List<Template> templates;

        private static final int DIALOG_DELETE = 0;

        private Template deleteReserve = null;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setHasOptionsMenu(true);
        }

        @Override
        public void onActivityCreated(Bundle savedInstanceState) {
            super.onActivityCreated(savedInstanceState);
            getListView().setOnItemLongClickListener((parent, view, position, id) -> {
                deleteReserve = templates.get(position);
                SimpleAlertDialogFragment dialogFragment = SimpleAlertDialogFragment.newInstance(
                        DIALOG_DELETE,
                        "確認", "設定を削除しますか?", "OK", "キャンセル"
                );
                dialogFragment.setTargetFragment(InnerFragment.this, 1);
                dialogFragment.show(getChildFragmentManager(), "alert");
                return true;
            });

        }

        @Override
        public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
            inflater.inflate(R.menu.template, menu);
        }

        @Override
        public void onListItemClick(ListView l, View v, int position, long id) {
            Template template = templates.get(position);
            TemplateEditDialogFragment dialogFragment = TemplateEditDialogFragment.newInstance(template, this);
            dialogFragment.show(getFragmentManager(), "edit");
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            switch (item.getItemId()) {
                case R.id.action_add: {
                    TemplateEditDialogFragment dialogFragment = TemplateEditDialogFragment.newInstance(null, this);
                    dialogFragment.show(getFragmentManager(), "add");
                    return true;
                }
            }
            return super.onOptionsItemSelected(item);
        }

        private CentralDatabase getDatabase() {
            return ((TemplateEditActivity) getActivity()).getTwitterService().getDatabase();
        }

        public void reloadList() {
            templates = getDatabase().getRecords(Template.class);
            setListAdapter(new ArrayAdapter<>(getActivity(), android.R.layout.simple_list_item_1, templates));
        }

        public void updateTemplate(Template template) {
            getDatabase().updateRecord(template);
            reloadList();
        }

        @Override
        public void onDialogChose(int requestCode, int which) {
            if (which == DialogInterface.BUTTON_POSITIVE && requestCode == DIALOG_DELETE && deleteReserve != null) {
                getDatabase().deleteRecord(deleteReserve);
                reloadList();
                Toast.makeText(getActivity(), "削除しました", Toast.LENGTH_LONG).show();
                deleteReserve = null;
            }
        }
    }

    public static class TemplateEditDialogFragment extends DialogFragment {
        public static final String ARG_TEMPLATE = "template";

        public static TemplateEditDialogFragment newInstance(Template template, Fragment target) {
            TemplateEditDialogFragment fragment = new TemplateEditDialogFragment();
            Bundle args = new Bundle();
            args.putSerializable(ARG_TEMPLATE, template);
            fragment.setArguments(args);
            fragment.setTargetFragment(target, 1);
            return fragment;
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final EditText editText = new EditText(getActivity());

            Template template = (Template) getArguments().getSerializable(ARG_TEMPLATE);
            if (template != null) {
                editText.setText(template.getValue());
            }

            return new AlertDialog.Builder(getActivity())
                    .setTitle(template == null ? "新規追加" : "編集")
                    .setView(editText)
                    .setPositiveButton("OK", (dialog, which) -> {
                        dismiss();
                        InnerFragment innerFragment = (InnerFragment) getTargetFragment();
                        if (innerFragment == null) {
                            throw new RuntimeException("TargetFragmentが設定されてないよ！！！１１");
                        }
                        Template template1 = (Template) getArguments().getSerializable(ARG_TEMPLATE);
                        if (template1 == null) {
                            template1 = new Template(editText.getText().toString());
                        } else {
                            template1.setValue(editText.getText().toString());
                        }
                        innerFragment.updateTemplate(template1);
                    })
                    .setNegativeButton("キャンセル", (dialog, which) -> {})
                    .create();
        }
    }
}
