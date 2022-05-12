package shibafu.yukari.activity;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.os.Bundle;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.fragment.app.ListFragment;
import androidx.appcompat.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import shibafu.yukari.R;
import shibafu.yukari.activity.base.ActionBarYukariBase;
import shibafu.yukari.database.AutoMuteConfig;
import shibafu.yukari.databinding.DialogAutoMuteBinding;
import shibafu.yukari.fragment.SimpleAlertDialogFragment;
import shibafu.yukari.service.TwitterService;

import java.util.List;

/**
 * Created by shibafu on 14/04/22.
 */
public class AutoMuteActivity extends ActionBarYukariBase{

    public static final String EXTRA_QUERY = "query";
    public static final String EXTRA_SCOPE = "scope";
    public static final String EXTRA_MATCH = "match";

    private static final String FRAGMENT_TAG = "inner";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_parent);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        if (savedInstanceState == null) {
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            InnerFragment fragment = new InnerFragment();
            transaction.replace(R.id.frame, fragment, FRAGMENT_TAG);
            transaction.commit();
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

    public InnerFragment findInnerFragment() {
        return ((InnerFragment)getSupportFragmentManager().findFragmentByTag(FRAGMENT_TAG));
    }

    @Override
    public void onServiceConnected() {
        findInnerFragment().reloadList();
    }

    @Override
    public void onServiceDisconnected() {

    }

    public static class InnerFragment extends ListFragment implements
            SimpleAlertDialogFragment.OnDialogChoseListener {

        private static final int DIALOG_DELETE = 0;

        private List<AutoMuteConfig> configs;
        private AutoMuteConfig deleteReserve = null;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setHasOptionsMenu(true);
        }

        @Override
        public void onActivityCreated(Bundle savedInstanceState) {
            super.onActivityCreated(savedInstanceState);
            getListView().setOnItemLongClickListener((parent, view, position, id) -> {
                deleteReserve = configs.get(position);
                SimpleAlertDialogFragment dialogFragment = SimpleAlertDialogFragment.newInstance(
                        DIALOG_DELETE,
                        "確認", "設定を削除しますか?", "OK", "キャンセル"
                );
                dialogFragment.setTargetFragment(InnerFragment.this, 1);
                dialogFragment.show(getFragmentManager(), "alert");
                return true;
            });

        }

        @Override
        public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
            inflater.inflate(R.menu.mute, menu);
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            switch (item.getItemId()) {
                case R.id.action_add: {
                    AutoMuteConfigDialogFragment dialogFragment = AutoMuteConfigDialogFragment.newInstance(null, this);
                    dialogFragment.show(getFragmentManager(), "config");
                    return true;
                }
            }
            return super.onOptionsItemSelected(item);
        }

        @Override
        public void onListItemClick(ListView l, View v, int position, long id) {
            AutoMuteConfig config = configs.get(position);
            AutoMuteConfigDialogFragment dialogFragment = AutoMuteConfigDialogFragment.newInstance(config, this);
            dialogFragment.show(getFragmentManager(), "config");
        }

        public void updateAutoMuteConfig(AutoMuteConfig config) {
            TwitterService twitterService = ((AutoMuteActivity) getActivity()).getTwitterService();
            twitterService.getDatabase().updateRecord(config);
            twitterService.updateAutoMuteConfig();

            reloadList();
        }

        public void reloadList() {
            configs = ((AutoMuteActivity) getActivity()).getTwitterService().getDatabase().getRecords(AutoMuteConfig.class);
            setListAdapter(new Adapter(getActivity(), configs));
        }

        @Override
        public void onDialogChose(int requestCode, int which, Bundle extras) {
            if (which == DialogInterface.BUTTON_POSITIVE) {
                switch (requestCode) {
                    case DIALOG_DELETE:
                        if (deleteReserve != null) {
                            TwitterService twitterService = ((AutoMuteActivity) getActivity()).getTwitterService();
                            twitterService.getDatabase().deleteRecord(deleteReserve);
                            twitterService.updateAutoMuteConfig();
                            reloadList();
                            Toast.makeText(getActivity(), "設定を削除しました", Toast.LENGTH_LONG).show();
                        }
                        deleteReserve = null;
                        break;
                }
            }
        }

        private class Adapter extends ArrayAdapter<AutoMuteConfig> {
            private LayoutInflater inflater;
            private String[] matchValues;

            public Adapter(Context context, List<AutoMuteConfig> objects) {
                super(context, 0, objects);
                inflater = (LayoutInflater) context.getSystemService(LAYOUT_INFLATER_SERVICE);

                Resources res = context.getResources();
                matchValues = res.getStringArray(R.array.mute_match_values);
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    convertView = inflater.inflate(android.R.layout.simple_list_item_2, null);
                }
                AutoMuteConfig item = getItem(position);
                if (item != null) {
                    TextView tv = (TextView) convertView.findViewById(android.R.id.text1);
                    tv.setText(item.getQuery());

                    tv = (TextView) convertView.findViewById(android.R.id.text2);
                    String text = matchValues[item.getMatch()];
                    tv.setText(text);
                }
                return convertView;
            }
        }
    }

    public static class AutoMuteConfigDialogFragment extends DialogFragment {
        private DialogAutoMuteBinding binding;

        public static AutoMuteConfigDialogFragment newInstance(AutoMuteConfig config, Fragment target) {
            AutoMuteConfigDialogFragment dialogFragment = new AutoMuteConfigDialogFragment();
            Bundle args = new Bundle();
            args.putSerializable("config", config);
            dialogFragment.setArguments(args);
            dialogFragment.setTargetFragment(target, 1);
            return dialogFragment;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            binding = DialogAutoMuteBinding.inflate(getLayoutInflater());

            AutoMuteConfig config = (AutoMuteConfig) getArguments().getSerializable("config");
            String title = "新規追加";
            if (config != null) {
                binding.etMuteTarget.setText(config.getQuery());
                binding.spMuteMatch.setSelection(config.getMatch());
                title = "編集";
            }

            AlertDialog dialog = new AlertDialog.Builder(getActivity())
                    .setTitle(title)
                    .setView(binding.getRoot())
                    .setPositiveButton("OK", (dialog1, which) -> {
                        // onDismissでnull代入してるから保持しないとNPEで死ぬ
                        DialogAutoMuteBinding binding = this.binding;

                        dismiss();
                        InnerFragment innerFragment = (InnerFragment) getTargetFragment();
                        if (innerFragment == null) {
                            throw new RuntimeException("TargetFragmentが設定されてないよ！！！１１");
                        }
                        AutoMuteConfig config1 = (AutoMuteConfig) getArguments().getSerializable("config");
                        if (config1 == null) {
                            config1 = new AutoMuteConfig(
                                    binding.spMuteMatch.getSelectedItemPosition(),
                                    binding.etMuteTarget.getText().toString()
                            );
                        } else {
                            config1.setMatch(binding.spMuteMatch.getSelectedItemPosition());
                            config1.setQuery(binding.etMuteTarget.getText().toString());
                        }
                        innerFragment.updateAutoMuteConfig(config1);
                    })
                    .setNegativeButton("キャンセル", (dialog1, which) -> {})
                    .create();

            return dialog;
        }

        @Override
        public void onDismiss(DialogInterface dialog) {
            super.onDismiss(dialog);
            binding = null;
        }
    }

}
