package shibafu.yukari.fragment;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
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
    public static final int MODE_EXPORT = 0;
    public static final int MODE_IMPORT = 1;

    private static final int REQUEST_RESOLVE_CONNECTION = 1;
    private static final int REQUEST_ERROR_SERVICE_AVAIL = 2;

    private GoogleApiClient apiClient;
    private byte[] data;
    private String fileName;
    private String mimeType;

    public static interface OnDriveImportCompletedListener {
        void onDriveImportCompleted(byte[] bytes);
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
                importEntries();
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
        if (requestCode == REQUEST_RESOLVE_CONNECTION && resultCode == Activity.RESULT_OK) {
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
                    Toast.makeText(getActivity(), "Import failed.", Toast.LENGTH_SHORT).show();
                    dismiss();
                } else {
                    file = Drive.DriveApi.getFile(apiClient, result.getMetadataBuffer().get(0).getDriveId());
                    file.openContents(apiClient, DriveFile.MODE_READ_ONLY, null).setResultCallback(resultCallback);
                }
            }

            private ResultCallback<DriveApi.ContentsResult> resultCallback = new ResultCallback<DriveApi.ContentsResult>() {
                @Override
                public void onResult(DriveApi.ContentsResult result) {
                    if (!result.getStatus().isSuccess()) {
                        Toast.makeText(getActivity(), "Import failed.", Toast.LENGTH_SHORT).show();
                        dismiss();
                        return;
                    }
                    Contents contents = result.getContents();

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
                    }
                    file.commitAndCloseContents(apiClient, contents);

                    OnDriveImportCompletedListener listener = (OnDriveImportCompletedListener) getTargetFragment();
                    listener.onDriveImportCompleted(baos.toByteArray());
                    Toast.makeText(getActivity(), "Import Complete!", Toast.LENGTH_SHORT).show();
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
                    Toast.makeText(getActivity(), "Export failed.", Toast.LENGTH_SHORT).show();
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
                        os.write(data);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    MetadataChangeSet metadata = new MetadataChangeSet.Builder()
                            .setTitle(fileName)
                            .setMimeType(mimeType)
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
