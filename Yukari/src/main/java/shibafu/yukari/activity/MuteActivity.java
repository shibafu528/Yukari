package shibafu.yukari.activity;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.fragment.app.ListFragment;
import androidx.appcompat.app.AlertDialog;
import android.text.format.DateFormat;
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
import com.codetroopers.betterpickers.calendardatepicker.CalendarDatePickerDialogFragment;
import com.codetroopers.betterpickers.radialtimepicker.RadialTimePickerDialogFragment;
import shibafu.yukari.R;
import shibafu.yukari.activity.base.ActionBarYukariBase;
import shibafu.yukari.database.MuteConfig;
import shibafu.yukari.database.MuteMatch;
import shibafu.yukari.databinding.DialogMuteBinding;
import shibafu.yukari.fragment.SimpleAlertDialogFragment;
import shibafu.yukari.service.TwitterService;
import shibafu.yukari.util.StringUtil;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * Created by shibafu on 14/04/22.
 */
public class MuteActivity extends ActionBarYukariBase{

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

            Intent intent = getIntent();
            if (intent.hasExtra(EXTRA_QUERY)) {
                MuteConfig config = new MuteConfig(
                        intent.getIntExtra(EXTRA_SCOPE, 0),
                        intent.getIntExtra(EXTRA_MATCH, MuteMatch.MATCH_EXACT),
                        MuteConfig.MUTE_TWEET,
                        intent.getStringExtra(EXTRA_QUERY)
                );
                MuteConfigDialogFragment dialogFragment = MuteConfigDialogFragment.newInstance(config, fragment);
                dialogFragment.show(getSupportFragmentManager(), "config");
            }
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

        private List<MuteConfig> configs;
        private MuteConfig deleteReserve = null;

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
                    MuteConfigDialogFragment dialogFragment = MuteConfigDialogFragment.newInstance(null, this);
                    dialogFragment.show(getFragmentManager(), "config");
                    return true;
                }
            }
            return super.onOptionsItemSelected(item);
        }

        @Override
        public void onListItemClick(ListView l, View v, int position, long id) {
            MuteConfig config = configs.get(position);
            MuteConfigDialogFragment dialogFragment = MuteConfigDialogFragment.newInstance(config, this);
            dialogFragment.show(getFragmentManager(), "config");
        }

        public void updateMuteConfig(MuteConfig config) {
            TwitterService twitterService = ((MuteActivity) getActivity()).getTwitterService();
            twitterService.getDatabase().updateRecord(config);

            reloadList();
        }

        public void reloadList() {
            configs = ((MuteActivity) getActivity()).getTwitterService().getDatabase().getRecords(MuteConfig.class);
            setListAdapter(new Adapter(getActivity(), configs));
        }

        @Override
        public void onDialogChose(int requestCode, int which, Bundle extras) {
            if (which == DialogInterface.BUTTON_POSITIVE) {
                switch (requestCode) {
                    case DIALOG_DELETE:
                        if (deleteReserve != null) {
                            TwitterService twitterService = ((MuteActivity) getActivity()).getTwitterService();
                            twitterService.getDatabase().deleteRecord(deleteReserve);
                            reloadList();
                            Toast.makeText(getActivity(), "設定を削除しました", Toast.LENGTH_LONG).show();
                        }
                        deleteReserve = null;
                        break;
                }
            }
        }

        private class Adapter extends ArrayAdapter<MuteConfig> {
            private LayoutInflater inflater;
            private String[] targetValues, matchValues, eraseValues;

            public Adapter(Context context, List<MuteConfig> objects) {
                super(context, 0, objects);
                inflater = (LayoutInflater) context.getSystemService(LAYOUT_INFLATER_SERVICE);

                Resources res = context.getResources();

                targetValues = res.getStringArray(R.array.mute_target_values);
                matchValues = res.getStringArray(R.array.mute_match_values);
                eraseValues = res.getStringArray(R.array.mute_erase_values);
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    convertView = inflater.inflate(android.R.layout.simple_list_item_2, null);
                }
                MuteConfig item = getItem(position);
                if (item != null) {
                    TextView tv = (TextView) convertView.findViewById(android.R.id.text1);
                    tv.setText(item.getQuery());

                    tv = (TextView) convertView.findViewById(android.R.id.text2);
                    String text = String.format("%s[%s] - %s",
                            targetValues[item.getScope()],
                            matchValues[item.getMatch()],
                            eraseValues[item.getMute()]);
                    if (item.isTimeLimited()) {
                        text = String.format("%s\n有効期限: %s",
                                text,
                                StringUtil.formatDate(item.getExpirationTimeMillis())
                                );
                    }
                    tv.setText(text);
                }
                return convertView;
            }
        }
    }

    public static class MuteConfigDialogFragment extends DialogFragment {
        private final SimpleDateFormat dfDate = new SimpleDateFormat("yyyy/MM/dd");
        private final SimpleDateFormat dfTime = new SimpleDateFormat("HH:mm");
        private long expirationTimeMillis = -1;

        private DialogMuteBinding binding;

        public static MuteConfigDialogFragment newInstance(MuteConfig config, Fragment target) {
            MuteConfigDialogFragment dialogFragment = new MuteConfigDialogFragment();
            Bundle args = new Bundle();
            args.putSerializable("config", config);
            dialogFragment.setArguments(args);
            dialogFragment.setTargetFragment(target, 1);
            return dialogFragment;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            binding = DialogMuteBinding.inflate(getLayoutInflater());
            binding.btnMuteExpr.setOnClickListener(this::onClickExpireMenu);
            binding.etMuteExprDate.setOnClickListener(this::onClickDate);
            binding.etMuteExprTime.setOnClickListener(this::onClickTime);

            MuteConfig config = (MuteConfig) getArguments().getSerializable("config");
            String title = "新規追加";
            if (config != null) {
                expirationTimeMillis = config.getExpirationTimeMillis();

                binding.etMuteTarget.setText(config.getQuery());

                binding.spMuteTarget.setSelection(config.getScope());
                binding.spMuteMatch.setSelection(config.getMatch());
                binding.spMuteErase.setSelection(config.getMute());
                if (config.isTimeLimited()) {
                    binding.llMuteExprConfig.setVisibility(View.VISIBLE);
                    binding.llMuteExprNever.setVisibility(View.GONE);
                }
                updateExpire();

                title = "編集";
            }

            AlertDialog dialog = new AlertDialog.Builder(getActivity())
                    .setTitle(title)
                    .setView(binding.getRoot())
                    .setPositiveButton("OK", (dialog1, which) -> {
                        DialogMuteBinding binding = this.binding;

                        dismiss();
                        InnerFragment innerFragment = (InnerFragment) getTargetFragment();
                        if (innerFragment == null) {
                            throw new RuntimeException("TargetFragmentが設定されてないよ！！！１１");
                        }
                        MuteConfig config1 = (MuteConfig) getArguments().getSerializable("config");
                        if (config1 == null) {
                            config1 = new MuteConfig(binding.spMuteTarget.getSelectedItemPosition(),
                                    binding.spMuteMatch.getSelectedItemPosition(),
                                    binding.spMuteErase.getSelectedItemPosition(),
                                    binding.etMuteTarget.getText().toString(),
                                    expirationTimeMillis);
                        } else {
                            config1.setScope(binding.spMuteTarget.getSelectedItemPosition());
                            config1.setMatch(binding.spMuteMatch.getSelectedItemPosition());
                            config1.setMute(binding.spMuteErase.getSelectedItemPosition());
                            config1.setQuery(binding.etMuteTarget.getText().toString());
                            config1.setExpirationTimeMillis(expirationTimeMillis);
                        }
                        innerFragment.updateMuteConfig(config1);
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

        private void updateExpire() {
            Date date = new Date(expirationTimeMillis);
            binding.etMuteExprDate.setText(dfDate.format(date));
            binding.etMuteExprTime.setText(dfTime.format(date));
        }

        void onClickExpireMenu(View v) {
            AlertDialog dialog = new AlertDialog.Builder(getActivity())
                    .setItems(R.array.mute_expr, (dialog1, which) -> {
                        Calendar c = Calendar.getInstance();
                        c.set(Calendar.SECOND, 0);
                        switch (which) {
                            case 0:
                                c.add(Calendar.MINUTE, 30);
                                break;
                            case 1:
                                c.add(Calendar.HOUR_OF_DAY, 1);
                                break;
                            case 2:
                                c.add(Calendar.HOUR_OF_DAY, 3);
                                break;
                            case 3:
                                c.add(Calendar.HOUR_OF_DAY, 12);
                                break;
                            case 4:
                                c.add(Calendar.DAY_OF_MONTH, 1);
                                break;
                            case 6:
                                c.setTimeInMillis(0);
                                break;
                        }
                        expirationTimeMillis = c.getTimeInMillis();
                        updateExpire();
                        if (expirationTimeMillis > 0) {
                            binding.llMuteExprConfig.setVisibility(View.VISIBLE);
                            binding.llMuteExprNever.setVisibility(View.GONE);
                        } else {
                            binding.llMuteExprConfig.setVisibility(View.GONE);
                            binding.llMuteExprNever.setVisibility(View.VISIBLE);
                        }
                    })
                    .create();
            dialog.show();
        }

        void onClickDate(View v) {
            binding.etMuteExprDate.clearFocus();

            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(expirationTimeMillis);

            CalendarDatePickerDialogFragment dialog = new CalendarDatePickerDialogFragment()
                    .setOnDateSetListener((calendarDatePickerDialog, i, i2, i3) -> {
                        Calendar c1 = Calendar.getInstance();
                        c1.setTimeInMillis(expirationTimeMillis);
                        c1.set(i, i2, i3);
                        expirationTimeMillis = c1.getTimeInMillis();
                        updateExpire();
                    })
                    .setDoneText("OK")
                    .setCancelText("キャンセル")
                    .setPreselectedDate(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
            dialog.show(getFragmentManager(), null);
        }

        void onClickTime(View v) {
            binding.etMuteExprTime.clearFocus();

            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(expirationTimeMillis);

            RadialTimePickerDialogFragment dialog = new RadialTimePickerDialogFragment()
                    .setOnTimeSetListener((dialog1, hourOfDay, minute) -> {
                        Calendar c1 = Calendar.getInstance();
                        c1.setTimeInMillis(expirationTimeMillis);
                        c1.set(Calendar.HOUR_OF_DAY, hourOfDay);
                        c1.set(Calendar.MINUTE, minute);
                        expirationTimeMillis = c1.getTimeInMillis();
                        updateExpire();
                    })
                    .setDoneText("OK")
                    .setCancelText("キャンセル")
                    .setStartTime(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
            if (DateFormat.is24HourFormat(getActivity())) {
                dialog.setForced24hFormat();
            }
            dialog.show(getFragmentManager(), null);
        }
    }

}
