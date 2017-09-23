package shibafu.yukari.fragment;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.widget.Toast;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.SupportErrorDialogFragment;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveApi;
import com.google.android.gms.drive.DriveContents;
import com.google.android.gms.drive.DriveFile;
import com.google.android.gms.drive.DriveFolder;
import com.google.android.gms.drive.MetadataChangeSet;
import com.google.android.gms.drive.query.Filters;
import com.google.android.gms.drive.query.Query;
import com.google.android.gms.drive.query.SearchableField;
import shibafu.yukari.R;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
* Created by shibafu on 14/07/05.
*/
public class DriveConnectionDialogFragment extends DialogFragment implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener{
    public static final int MODE_SIGN_OUT = -1;
    public static final int MODE_EXPORT   = 0;
    public static final int MODE_IMPORT   = 1;

    private static final String LOG_TAG = "DriveConnectionDialog";
    private static final int REQUEST_RESOLVE_CONNECTION = 1;
    private static final int REQUEST_ERROR_SERVICE_AVAIL = 2;

    private GoogleApiClient apiClient;

    private byte[] data;
    private String fileName;
    private String mimeType;
    private boolean isCancelled;

    public static interface OnDriveImportCompletedListener {
        void onDriveImportCompleted(byte[] bytes);
    }

    public static DriveConnectionDialogFragment newInstance(int mode, String... args) {
        DriveConnectionDialogFragment fragment = new DriveConnectionDialogFragment();
        Bundle arg = new Bundle();
        arg.putInt("mode", mode);
        int count = args.length;
        String key = null;
        for (String s : args) {
            if (key == null) {
                key = s;
            } else {
                arg.putString(key, s);
                key = null;
            }
        }
        fragment.setArguments(arg);
        return fragment;
    }

    public static DriveConnectionDialogFragment newInstance(String fileName, String mimeType, Fragment callback) {
        DriveConnectionDialogFragment fragment = new DriveConnectionDialogFragment();
        Bundle args = new Bundle();
        args.putInt("mode", MODE_IMPORT);
        args.putString("name", fileName);
        args.putString("mime", mimeType);
        fragment.setArguments(args);
        fragment.setTargetFragment(callback, 0);
        return fragment;
    }

    public static DriveConnectionDialogFragment newInstance(String fileName, String mimeType, byte[] data) {
        DriveConnectionDialogFragment fragment = new DriveConnectionDialogFragment();
        Bundle args = new Bundle();
        args.putInt("mode", MODE_EXPORT);
        args.putByteArray("data", data);
        args.putString("name", fileName);
        args.putString("mime", mimeType);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        Bundle args = getArguments();
        data = args.getByteArray("data");
        fileName = args.getString("name");
        mimeType = args.getString("mime");
        apiClient = new GoogleApiClient.Builder(getActivity())
                .addApi(Drive.API)
                .addScope(Drive.SCOPE_FILE)
                .addScope(Drive.SCOPE_APPFOLDER)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
    }

    @Override
    public void onStart() {
        super.onStart();
        apiClient.connect();
    }

    @Override
    public void onStop() {
        super.onStop();
        apiClient.disconnect();
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Bundle args = getArguments();
        String message;
        switch (args.getInt("mode", 0)) {
            case MODE_SIGN_OUT:
                message = "サインアウト中...";
                break;
            case MODE_IMPORT:
                message = getActivity().getString(R.string.drive_importing);
                break;
            case MODE_EXPORT:
                message = getActivity().getString(R.string.drive_exporting);
                break;
            default:
                throw new IllegalArgumentException("mode extraが正しく指定されていません.");
        }
        ProgressDialog dialog = ProgressDialog.show(getActivity(),
                null,
                message,
                true,
                false);
        dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        return dialog;
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        super.onCancel(dialog);
        isCancelled = true;
    }

    @Override
    public void onConnected(Bundle bundle) {
        Log.d(LOG_TAG, "onConnected");
        Bundle args = getArguments();
        switch (args.getInt("mode", MODE_EXPORT)) {
            case MODE_EXPORT:
                exportEntries();
                break;
            case MODE_IMPORT:
                importEntries();
                break;
            case MODE_SIGN_OUT:
                apiClient.clearDefaultAccountAndReconnect();
                dismiss();
                break;
        }
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d(LOG_TAG, "onConnectionSuspended");
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.d(LOG_TAG, "onConnectionFailed");
        if (!connectionResult.hasResolution()) {
            GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
            apiAvailability.getErrorDialog(getActivity(), connectionResult.getErrorCode(), 0).show();
            dismiss();
            return;
        }
        try {
            connectionResult.startResolutionForResult(getActivity(), REQUEST_RESOLVE_CONNECTION);
        } catch (IntentSender.SendIntentException e) {
            e.printStackTrace();
            Toast.makeText(getActivity(), getActivity().getString(R.string.drive_error_connect), Toast.LENGTH_LONG).show();
        } finally {
            dismiss();
        }
        Toast.makeText(getActivity(), "画面の指示に従った後、もう一度操作をお試しください。", Toast.LENGTH_LONG).show();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(LOG_TAG, "onActivityResult");
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_RESOLVE_CONNECTION && resultCode == Activity.RESULT_OK) {
            apiClient.connect();
        }
    }

    private boolean checkServiceAvailable() {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        int resultCode = apiAvailability.isGooglePlayServicesAvailable(getActivity()) ;
        if (resultCode != ConnectionResult.SUCCESS) {
            Dialog dialog = apiAvailability.getErrorDialog(getActivity(), resultCode, REQUEST_ERROR_SERVICE_AVAIL);
            if (dialog != null) {
                SupportErrorDialogFragment.newInstance(dialog).show(getFragmentManager(), "error_service_avail");
            }
            dismiss();
            return false;
        }
        return true;
    }

    private void importEntries() {
        if (!checkServiceAvailable() || getTargetFragment() == null ||
                !(getTargetFragment() instanceof OnDriveImportCompletedListener)) {
            dismiss();
            return;
        }

        Query query = new Query.Builder().addFilter(Filters.eq(SearchableField.TITLE, fileName)).build();
        final DriveFolder appFolder = Drive.DriveApi.getAppFolder(apiClient);
        appFolder.queryChildren(apiClient, query).setResultCallback(new ResultCallback<DriveApi.MetadataBufferResult>() {
            private DriveFile file;

            @Override
            public void onResult(DriveApi.MetadataBufferResult result) {
                if (!result.getStatus().isSuccess() || result.getMetadataBuffer().getCount() < 1) {
                    Toast.makeText(getActivity(), getActivity().getString(R.string.drive_failed_import), Toast.LENGTH_SHORT).show();
                    dismiss();
                } else {
                    file = result.getMetadataBuffer().get(0).getDriveId().asDriveFile();
                    file.open(apiClient, DriveFile.MODE_READ_ONLY, null).setResultCallback(resultCallback);
                }
            }

            private ResultCallback<DriveApi.DriveContentsResult> resultCallback = result -> {
                if (!result.getStatus().isSuccess()) {
                    Toast.makeText(getActivity(), getActivity().getString(R.string.drive_failed_import), Toast.LENGTH_SHORT).show();
                    dismiss();
                    return;
                }
                DriveContents contents = result.getDriveContents();

                InputStream is = contents.getInputStream();
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int length;
                try {
                    while ((length = is.read(buffer, 0, buffer.length)) != -1) {
                        baos.write(buffer, 0, length);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    try {
                        baos.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                contents.discard(apiClient);

                if (!isCancelled) {
                    OnDriveImportCompletedListener listener = (OnDriveImportCompletedListener) getTargetFragment();
                    listener.onDriveImportCompleted(baos.toByteArray());
                    Toast.makeText(getActivity(), getActivity().getString(R.string.drive_complete_import), Toast.LENGTH_SHORT).show();
                    dismiss();
                }
            };
        });
    }

    private void exportEntries() {
        if (!checkServiceAvailable()) {
            dismiss();
            return;
        }

        Query query = new Query.Builder().addFilter(Filters.eq(SearchableField.TITLE, fileName)).build();
        final DriveFolder appFolder = Drive.DriveApi.getAppFolder(apiClient);
        appFolder.queryChildren(apiClient, query).setResultCallback(new ResultCallback<DriveApi.MetadataBufferResult>() {
            private DriveFile existFile;

            @Override
            public void onResult(DriveApi.MetadataBufferResult result) {
                if (!result.getStatus().isSuccess()) {
                    Toast.makeText(getActivity(), getActivity().getString(R.string.drive_failed_export), Toast.LENGTH_SHORT).show();
                    dismiss();
                    return;
                }
                if (result.getMetadataBuffer().getCount() < 1) {
                    Drive.DriveApi.newDriveContents(apiClient).setResultCallback(resultCallback);
                } else {
                    existFile = result.getMetadataBuffer().get(0).getDriveId().asDriveFile();
                    existFile.open(apiClient, DriveFile.MODE_WRITE_ONLY, null).setResultCallback(resultCallback);
                }
            }

            private ResultCallback<DriveApi.DriveContentsResult> resultCallback = new ResultCallback<DriveApi.DriveContentsResult>() {
                @Override
                public void onResult(DriveApi.DriveContentsResult result) {
                    if (!result.getStatus().isSuccess()) {
                        Toast.makeText(getActivity(), getActivity().getString(R.string.drive_failed_export), Toast.LENGTH_SHORT).show();
                        dismiss();
                        return;
                    }
                    DriveContents contents = result.getDriveContents();

                    OutputStream os = contents.getOutputStream();
                    try {
                        os.write(data);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    MetadataChangeSet metadata = new MetadataChangeSet.Builder()
                            .setTitle(fileName)
                            .setMimeType(mimeType)
                            .build();

                    if (!isCancelled) {
                        if (existFile == null) {
                            appFolder.createFile(apiClient, metadata, contents).setResultCallback(driveFileResult -> showResultMessage(driveFileResult.getStatus()));
                        } else {
                            contents.commit(apiClient, null).setResultCallback(status -> showResultMessage(status.getStatus()));
                        }
                    }
                }

                private void showResultMessage(Status status) {
                    if (status.isSuccess()) {
                        Toast.makeText(getActivity(), getActivity().getString(R.string.drive_complete_export), Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(getActivity(), getActivity().getString(R.string.drive_failed_export), Toast.LENGTH_SHORT).show();
                    }
                    dismiss();
                }
            };
        });
    }

}
