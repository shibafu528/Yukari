package shibafu.yukari.fragment;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import androidx.fragment.app.DialogFragment;
import androidx.appcompat.app.AlertDialog;
import android.text.TextUtils;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import shibafu.yukari.R;
import shibafu.yukari.common.async.ThrowableTwitterAsyncTask;
import shibafu.yukari.service.TwitterServiceDelegate;
import shibafu.yukari.database.AuthUserRecord;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.UserList;

/**
 * Created by shibafu on 14/08/05.
 */
public class UserListEditDialogFragment extends DialogFragment {

    private static final String ARG_REQUEST_CODE = "requestCode";
    private static final String ARG_USER = "user";
    private static final String ARG_TARGETLIST = "list";

    @BindView(R.id.etName) EditText etName;
    @BindView(R.id.etDescription) EditText etDesctiption;
    @BindView(R.id.checkBox) CheckBox cbPrivate;

    private boolean createNewList;
    private AuthUserRecord userRecord;
    private UserList targetList;
    private int requestCode;
    private Unbinder unbinder;

    public static UserListEditDialogFragment newInstance(AuthUserRecord userRecord, int requestCode) {
        UserListEditDialogFragment fragment = new UserListEditDialogFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_USER, userRecord);
        args.putInt(ARG_REQUEST_CODE, requestCode);
        fragment.setArguments(args);
        return fragment;
    }

    public static UserListEditDialogFragment newInstance(AuthUserRecord userRecord, UserList targetList, int requestCode) {
        UserListEditDialogFragment fragment = new UserListEditDialogFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_USER, userRecord);
        args.putSerializable(ARG_TARGETLIST, targetList);
        args.putInt(ARG_REQUEST_CODE, requestCode);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        View v = getActivity().getLayoutInflater().inflate(R.layout.dialog_listedit, null);
        unbinder = ButterKnife.bind(this, v);

        String title;
        Bundle args = getArguments();
        userRecord = (AuthUserRecord) args.getSerializable(ARG_USER);
        targetList = (UserList) args.getSerializable(ARG_TARGETLIST);
        requestCode = args.getInt(ARG_REQUEST_CODE);
        if (targetList != null) {
            title = "編集";
            etName.setText(targetList.getName());
            etDesctiption.setText(targetList.getDescription());
            cbPrivate.setChecked(!targetList.isPublic());
        } else {
            title = "新規作成";
            createNewList = true;
        }

        AlertDialog dialog = new AlertDialog.Builder(getActivity())
                .setTitle(title)
                .setView(v)
                .setPositiveButton("OK", null)
                .setNegativeButton("キャンセル", null)
                .create();

        return dialog;
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        unbinder.unbind();
    }

    @Override
    public void onStart() {
        super.onStart();
        ((AlertDialog)getDialog()).getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                class Params {
                    public String title;
                    public String description;
                    public boolean isPrivate;

                    Params(String title, String description, boolean isPrivate) {
                        this.title = title;
                        this.description = description;
                        this.isPrivate = isPrivate;
                    }
                }

                String listTitle = etName.getText().toString();
                if (TextUtils.isEmpty(listTitle)) {
                    Toast.makeText(getActivity(), "リスト名が入力されていません", Toast.LENGTH_SHORT).show();
                    return;
                }

                TwitterServiceDelegate delegate = null;
                if (getTargetFragment() instanceof TwitterServiceDelegate) {
                    delegate = (TwitterServiceDelegate) getTargetFragment();
                } else if (getActivity() instanceof TwitterServiceDelegate) {
                    delegate = (TwitterServiceDelegate) getActivity();
                }

                new ThrowableTwitterAsyncTask<Params, Void>(delegate) {

                    private PostProgressDialogFragment dialogFragment;

                    @Override
                    protected void showToast(String message) {
                        Toast.makeText(getActivity(), message, Toast.LENGTH_LONG).show();
                    }

                    @Override
                    protected ThrowableResult<Void> doInBackground(Params... params) {
                        Twitter twitter = getTwitterInstance(userRecord);
                        try {
                            if (createNewList) {
                                twitter.createUserList(params[0].title, !params[0].isPrivate, params[0].description);
                            } else {
                                twitter.updateUserList(targetList.getId(), params[0].title, !params[0].isPrivate, params[0].description);
                            }
                            return new ThrowableResult<>((Void)null);
                        } catch (TwitterException e) {
                            e.printStackTrace();
                            return new ThrowableResult<>(e);
                        }
                    }

                    @Override
                    protected void onPreExecute() {
                        super.onPreExecute();
                        dialogFragment = PostProgressDialogFragment.newInstance();
                        dialogFragment.show(getFragmentManager(), "dialog");
                    }

                    @Override
                    protected void onPostExecute(ThrowableResult<Void> result) {
                        super.onPostExecute(result);
                        dialogFragment.dismiss();
                        if (!result.isException()) {
                            Toast.makeText(getActivity(), "リストの編集に成功しました", Toast.LENGTH_SHORT).show();
                            dismiss();

                            SimpleAlertDialogFragment.OnDialogChoseListener listener = null;
                            if (getTargetFragment() instanceof SimpleAlertDialogFragment.OnDialogChoseListener) {
                                listener = (SimpleAlertDialogFragment.OnDialogChoseListener) getTargetFragment();
                            } else if (getActivity() instanceof SimpleAlertDialogFragment.OnDialogChoseListener) {
                                listener = (SimpleAlertDialogFragment.OnDialogChoseListener) getActivity();
                            }

                            if (listener != null) {
                                listener.onDialogChose(requestCode, DialogInterface.BUTTON_POSITIVE, null);
                            }
                        }
                    }
                }.executeParallel(new Params(
                        listTitle,
                        etDesctiption.getText().toString(),
                        cbPrivate.isChecked()));
            }
        });
    }
}
