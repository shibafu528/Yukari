package shibafu.yukari.activity;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.ServiceConnection;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.ListFragment;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.SupportErrorDialogFragment;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.drive.Contents;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveApi;
import com.google.android.gms.drive.DriveFile;
import com.google.android.gms.drive.DriveFolder;
import com.google.android.gms.drive.MetadataChangeSet;
import com.google.android.gms.drive.query.Filters;
import com.google.android.gms.drive.query.Query;
import com.google.android.gms.drive.query.SearchableField;
import com.google.gson.Gson;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import shibafu.yukari.R;
import shibafu.yukari.database.MuteConfig;
import shibafu.yukari.fragment.SimpleAlertDialogFragment;
import shibafu.yukari.service.TwitterService;
import shibafu.yukari.service.TwitterServiceDelegate;

/**
 * Created by shibafu on 14/04/22.
 */
public class MuteActivity extends ActionBarActivity implements TwitterServiceDelegate{

    public static final String EXTRA_QUERY = "query";
    public static final String EXTRA_SCOPE = "scope";

    private static final String FRAGMENT_TAG = "inner";
    private TwitterService service;
    private boolean serviceBound = false;

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
                        MuteConfig.MATCH_EXACT,
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

    @Override
    protected void onStart() {
        super.onStart();
        bindService(new Intent(this, TwitterService.class), connection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        unbindService(connection);
    }

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            TwitterService.TweetReceiverBinder binder = (TwitterService.TweetReceiverBinder) service;
            MuteActivity.this.service = binder.getService();
            serviceBound = true;

            findInnerFragment().reloadList();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
        }
    };

    public InnerFragment findInnerFragment() {
        return ((InnerFragment)getSupportFragmentManager().findFragmentByTag(FRAGMENT_TAG));
    }

    @Override
    public TwitterService getTwitterService() {
        return service;
    }

    public static class InnerFragment extends ListFragment implements DialogInterface.OnClickListener {

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
            getListView().setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
                @Override
                public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                    deleteReserve = configs.get(position);
                    SimpleAlertDialogFragment dialogFragment = SimpleAlertDialogFragment.newInstance(
                            "確認", "設定を削除しますか?", "OK", "キャンセル"
                    );
                    dialogFragment.setTargetFragment(InnerFragment.this, 1);
                    dialogFragment.show(getChildFragmentManager(), "alert");
                    return true;
                }
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
                case R.id.action_import: {
                    DriveConnectionDialogFragment dialogFragment = DriveConnectionDialogFragment.newInstance();
                    dialogFragment.setTargetFragment(this, 0);
                    dialogFragment.show(getFragmentManager(), "import");
                    return true;
                }
                case R.id.action_export: {
                    DriveConnectionDialogFragment dialogFragment = DriveConnectionDialogFragment.newInstance(configs);
                    dialogFragment.show(getFragmentManager(), "export");
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
            twitterService.getDatabase().updateMuteConfig(config);
            twitterService.updateMuteConfig();

            reloadList();
        }

        public void reloadList() {
            configs = ((MuteActivity) getActivity()).getTwitterService().getDatabase().getMuteConfig();
            setListAdapter(new Adapter(getActivity(), configs));
        }

        @Override
        public void onClick(DialogInterface dialog, int which) {
            if (which == DialogInterface.BUTTON_POSITIVE && deleteReserve != null) {
                TwitterService twitterService = ((MuteActivity) getActivity()).getTwitterService();
                twitterService.getDatabase().deleteMuteConfig(deleteReserve.getId());
                twitterService.updateMuteConfig();
                reloadList();
                Toast.makeText(getActivity(), "設定を削除しました", Toast.LENGTH_LONG).show();
            }
            deleteReserve = null;
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
                    tv.setText(String.format("%s[%s] - %s",
                            targetValues[item.getScope()],
                            matchValues[item.getMatch()],
                            eraseValues[item.getMute()]));
                }
                return convertView;
            }
        }
    }

    public static class MuteConfigDialogFragment extends DialogFragment {

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
            View v = getActivity().getLayoutInflater().inflate(R.layout.dialog_mute, null);
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
                v.setBackgroundColor(Color.WHITE);
            }
            final EditText edit = (EditText) v.findViewById(R.id.etMuteTarget);
            final Spinner spTarget = (Spinner) v.findViewById(R.id.spMuteTarget);
            final Spinner spMatch = (Spinner) v.findViewById(R.id.spMuteMatch);
            final Spinner spErase = (Spinner) v.findViewById(R.id.spMuteErase);

            MuteConfig config = (MuteConfig) getArguments().getSerializable("config");
            String title = "新規追加";
            if (config != null) {
                edit.setText(config.getQuery());

                spTarget.setSelection(config.getScope());
                spMatch.setSelection(config.getMatch());
                spErase.setSelection(config.getMute());

                title = "編集";
            }

            AlertDialog dialog = new AlertDialog.Builder(getActivity())
                    .setTitle(title)
                    .setView(v)
                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dismiss();
                            InnerFragment innerFragment = (InnerFragment) getTargetFragment();
                            if (innerFragment == null) {
                                throw new RuntimeException("TargetFragmentが設定されてないよ！！！１１");
                            }
                            MuteConfig config = (MuteConfig) getArguments().getSerializable("config");
                            if (config == null) {
                                config = new MuteConfig(spTarget.getSelectedItemPosition(),
                                        spMatch.getSelectedItemPosition(),
                                        spErase.getSelectedItemPosition(),
                                        edit.getText().toString());
                            } else {
                                config.setScope(spTarget.getSelectedItemPosition());
                                config.setMatch(spMatch.getSelectedItemPosition());
                                config.setMute(spErase.getSelectedItemPosition());
                                config.setQuery(edit.getText().toString());
                            }
                            innerFragment.updateMuteConfig(config);
                        }
                    })
                    .setNegativeButton("キャンセル", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                        }
                    })
                    .create();

            return dialog;
        }
    }

    public static class DriveConnectionDialogFragment extends DialogFragment implements
            GoogleApiClient.ConnectionCallbacks,
            GoogleApiClient.OnConnectionFailedListener{
        public static final int MODE_EXPORT = 0;
        public static final int MODE_IMPORT = 1;

        private static final int REQUEST_RESOLVE_CONNECTION = 1;
        private static final int REQUEST_ERROR_SERVICE_AVAIL = 2;

        private GoogleApiClient apiClient;
        private List<MuteConfig> configs;

        public static DriveConnectionDialogFragment newInstance() {
            DriveConnectionDialogFragment fragment = new DriveConnectionDialogFragment();
            Bundle args = new Bundle();
            args.putInt("mode", MODE_IMPORT);
            fragment.setArguments(args);
            return fragment;
        }

        public static DriveConnectionDialogFragment newInstance(List<MuteConfig> muteConfigs) {
            DriveConnectionDialogFragment fragment = new DriveConnectionDialogFragment();
            Bundle args = new Bundle();
            args.putInt("mode", MODE_EXPORT);
            args.putParcelableArrayList("entries", new ArrayList<>(muteConfigs));
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public void onActivityCreated(Bundle savedInstanceState) {
            super.onActivityCreated(savedInstanceState);
            Bundle args = getArguments();
            configs = args.getParcelableArrayList("entries");
            apiClient = new GoogleApiClient.Builder(getActivity())
                    .addApi(Drive.API)
                    .addScope(Drive.SCOPE_FILE)
                    .addScope(Drive.SCOPE_APPFOLDER)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();
            apiClient.connect();
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            Bundle args = getArguments();
            ProgressDialog dialog = ProgressDialog.show(getActivity(),
                    null,
                    args.getInt("mode", 0) == 0? "エクスポート中..." : "インポート中...",
                    true,
                    false);
            dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            return dialog;
        }

        @Override
        public void onConnected(Bundle bundle) {
            Bundle args = getArguments();
            switch (args.getInt("mode", MODE_EXPORT)) {
                case MODE_EXPORT:
                    exportEntries();
                    break;
                case MODE_IMPORT:
                    break;
            }
        }

        @Override
        public void onConnectionSuspended(int i) {

        }

        @Override
        public void onConnectionFailed(ConnectionResult connectionResult) {
            if (!connectionResult.hasResolution()) {
                GooglePlayServicesUtil.getErrorDialog(connectionResult.getErrorCode(), getActivity(), 0).show();
                return;
            }
            try {
                connectionResult.startResolutionForResult(getActivity(), REQUEST_RESOLVE_CONNECTION);
            } catch (IntentSender.SendIntentException e) {
                e.printStackTrace();
                Toast.makeText(getActivity(), "Driveとの接続に失敗しました", Toast.LENGTH_LONG).show();
            }
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            if (requestCode == REQUEST_RESOLVE_CONNECTION && resultCode == RESULT_OK) {
                apiClient.connect();
            }
        }

        private boolean checkServiceAvailable() {
            int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(getActivity()) ;
            if (resultCode != ConnectionResult.SUCCESS) {
                Dialog dialog = GooglePlayServicesUtil.getErrorDialog(resultCode, getActivity(), REQUEST_ERROR_SERVICE_AVAIL);
                if (dialog != null) {
                    SupportErrorDialogFragment.newInstance(dialog).show(getFragmentManager(), "error_service_avail");
                }
                return false;
            }
            return true;
        }

        private void importEntries() {
            if (!checkServiceAvailable() || getTargetFragment() == null) {
                dismiss();
                return;
            }
            //TODO: なんとか
        }

        private void exportEntries() {
            if (!checkServiceAvailable()) {
                dismiss();
                return;
            }

            Query query = new Query.Builder().addFilter(Filters.eq(SearchableField.TITLE, "mute.json")).build();
            final DriveFolder appFolder = Drive.DriveApi.getAppFolder(apiClient);
            appFolder.queryChildren(apiClient, query).setResultCallback(new ResultCallback<DriveApi.MetadataBufferResult>() {
                private DriveFile existFile;

                @Override
                public void onResult(DriveApi.MetadataBufferResult result) {
                    if (!result.getStatus().isSuccess()) {
                        Log.e("MuteExport", "query failed.");
                        dismiss();
                        return;
                    }
                    if (result.getMetadataBuffer().getCount() < 1) {
                        Drive.DriveApi.newContents(apiClient).setResultCallback(resultCallback);
                    } else {
                        existFile = Drive.DriveApi.getFile(apiClient, result.getMetadataBuffer().get(0).getDriveId());
                        existFile.openContents(apiClient, DriveFile.MODE_WRITE_ONLY, null).setResultCallback(resultCallback);
                    }
                }

                private ResultCallback<DriveApi.ContentsResult> resultCallback = new ResultCallback<DriveApi.ContentsResult>() {
                    @Override
                    public void onResult(DriveApi.ContentsResult result) {
                        if (!result.getStatus().isSuccess()) {
                            Toast.makeText(getActivity(), "Export failed.", Toast.LENGTH_SHORT).show();
                            dismiss();
                            return;
                        }
                        Contents contents = result.getContents();

                        OutputStream os = contents.getOutputStream();
                        try {
                            os.write(new Gson().toJson(configs).getBytes());
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                        MetadataChangeSet metadata = new MetadataChangeSet.Builder()
                                .setTitle("mute.json")
                                .setMimeType("application/json")
                                .build();

                        if (existFile == null) {
                            appFolder.createFile(apiClient, metadata, contents).setResultCallback(new ResultCallback<DriveFolder.DriveFileResult>() {
                                @Override
                                public void onResult(DriveFolder.DriveFileResult driveFileResult) {
                                    showResultMessage(driveFileResult.getStatus());
                                }
                            });
                        } else {
                            existFile.commitAndCloseContents(apiClient, contents).setResultCallback(new ResultCallback<Status>() {
                                @Override
                                public void onResult(Status status) {
                                    showResultMessage(status.getStatus());
                                }
                            });
                        }
                    }

                    private void showResultMessage(Status status) {
                        if (status.isSuccess()) {
                            Toast.makeText(getActivity(), "Export Complete!", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(getActivity(), "Export failed.", Toast.LENGTH_SHORT).show();
                        }
                        dismiss();
                    }
                };
            });
        }

    }
}
