package shibafu.yukari.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import androidx.fragment.app.ListFragment;
import androidx.appcompat.app.AlertDialog;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import com.sys1yagi.mastodon4j.MastodonClient;
import com.sys1yagi.mastodon4j.api.entity.Account;
import com.sys1yagi.mastodon4j.api.exception.Mastodon4jRequestException;
import com.sys1yagi.mastodon4j.api.method.Accounts;
import shibafu.yukari.R;
import shibafu.yukari.activity.base.ActionBarYukariBase;
import shibafu.yukari.common.async.ParallelAsyncTask;
import shibafu.yukari.common.async.ThrowableTwitterAsyncTask;
import shibafu.yukari.common.bitmapcache.ImageLoaderTask;
import shibafu.yukari.core.App;
import shibafu.yukari.database.Provider;
import shibafu.yukari.fragment.ColorPickerDialogFragment;
import shibafu.yukari.mastodon.MastodonUtil;
import shibafu.yukari.service.TwitterService;
import shibafu.yukari.service.TwitterServiceDelegate;
import shibafu.yukari.database.AuthUserRecord;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.User;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Shibafu on 13/12/21.
 */
public class AccountManageActivity extends ActionBarYukariBase {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_parent);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.frame, new AccountListFragment(), "list")
                    .commit();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.accountmanage, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                break;
            case R.id.action_add:
                startActivity(new Intent(this, OAuthActivity.class));
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onServiceConnected() {
        AccountListFragment listFragment = (AccountListFragment) getSupportFragmentManager().findFragmentByTag("list");
        if (listFragment != null) {
            listFragment.createList();
        }
    }

    @Override
    public void onServiceDisconnected() {}

    public static class AccountListFragment extends ListFragment implements ColorPickerDialogFragment.ColorPickerCallback{
        private TwitterServiceDelegate delegate;
        private Adapter adapter;
        private List<AuthUserRecord> dataList = new ArrayList<>();

        @Override
        public void onAttach(Context context) {
            super.onAttach(context);
            delegate = (TwitterServiceDelegate) context;
        }

        public void createList() {
            dataList.clear();
            dataList.addAll(App.getInstance(requireContext()).getAccountManager().getUsers());
            adapter = new Adapter(getActivity(), dataList);
            setListAdapter(adapter);
        }

        @Override
        public void onListItemClick(ListView l, View v, final int position, long id) {
            super.onListItemClick(l, v, position, id);

            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                    .setTitle("メニュー")
                    .setItems(new String[]{"プライマリに設定", "アカウントカラーを設定", "名前とアイコンの再取得", "認証情報を削除"}, (dialog, which) -> {
                        switch (which) {
                            case 0:
                                App.getInstance(requireContext()).getAccountManager().setPrimaryUser(dataList.get(position).InternalId);
                                createList();
                                break;
                            case 1:
                                ColorPickerDialogFragment dialogFragment
                                        = ColorPickerDialogFragment.newInstance(
                                        dataList.get(position).AccountColor,
                                        String.valueOf(dataList.get(position).InternalId));
                                dialogFragment.setTargetFragment(AccountListFragment.this, 1);
                                dialogFragment.show(getFragmentManager(), "color");
                                break;
                            case 2:
                                switch (dataList.get(position).Provider.getApiType()) {
                                    case Provider.API_TWITTER:
                                        new ThrowableTwitterAsyncTask<AuthUserRecord, twitter4j.User>(delegate) {

                                            @Override
                                            protected ThrowableResult<User> doInBackground(AuthUserRecord... authUserRecords) {
                                                try {
                                                    Twitter twitter = getTwitterInstance(authUserRecords[0]);
                                                    User user = twitter.showUser(authUserRecords[0].NumericId);
                                                    return new ThrowableResult<>(user);
                                                } catch (TwitterException e) {
                                                    e.printStackTrace();
                                                    return new ThrowableResult<>(e);
                                                }
                                            }

                                            @Override
                                            protected void onPostExecute(ThrowableResult<User> result) {
                                                super.onPostExecute(result);
                                                User user = result.getResult();
                                                if (user != null) {
                                                    App app = App.getInstance(getActivity());
                                                    app.getDatabase().updateAccountProfile(
                                                            Provider.TWITTER.getId(), user.getId(), user.getScreenName(), user.getName(), user.getOriginalProfileImageURLHttps()
                                                    );
                                                    app.getAccountManager().reloadUsers();
                                                    createList();
                                                    Toast.makeText(getActivity(), "更新しました。", Toast.LENGTH_SHORT).show();
                                                }
                                            }

                                            @Override
                                            protected void showToast(String message) {
                                                Toast.makeText(getActivity(), message, Toast.LENGTH_SHORT).show();
                                            }
                                        }.executeParallel(dataList.get(position));
                                        break;
                                    case Provider.API_MASTODON:
                                        new ParallelAsyncTask<AuthUserRecord, Void, Account>() {

                                            private Mastodon4jRequestException exception;
                                            private AuthUserRecord userRecord;

                                            @Override
                                            protected Account doInBackground(AuthUserRecord... authUserRecords) {
                                                userRecord = authUserRecords[0];
                                                try {
                                                    MastodonClient api = (MastodonClient) App.getInstance(requireContext()).getProviderApi(authUserRecords[0]).getApiClient(authUserRecords[0]);
                                                    Accounts accounts = new Accounts(api);
                                                    Account result = accounts.getAccount(authUserRecords[0].NumericId).execute();
                                                    return result;
                                                } catch (Mastodon4jRequestException e) {
                                                    exception = e;
                                                    return null;
                                                }
                                            }

                                            @Override
                                            protected void onPostExecute(Account account) {
                                                super.onPostExecute(account);
                                                if (exception != null || account == null) {
                                                    Toast.makeText(getActivity(), "エラーが発生しました。", Toast.LENGTH_SHORT).show();
                                                    return;
                                                }

                                                App app = App.getInstance(getActivity());
                                                app.getDatabase().updateAccountProfile(
                                                        userRecord.Provider.getId(), account.getId(),
                                                        MastodonUtil.INSTANCE.expandFullScreenName(account.getAcct(), account.getUrl()),
                                                        TextUtils.isEmpty(account.getDisplayName()) ? account.getUserName() : account.getDisplayName(),
                                                        account.getAvatar()
                                                );
                                                app.getAccountManager().reloadUsers();
                                                createList();
                                                Toast.makeText(getActivity(), "更新しました。", Toast.LENGTH_SHORT).show();
                                            }
                                        }.executeParallel(dataList.get(position));
                                        break;
                                    default:
                                        Toast.makeText(getActivity(), "このアカウントでは使えない機能です。", Toast.LENGTH_SHORT).show();
                                        break;
                                }
                                break;
                            case 3:
                                AlertDialog alertDialog = new AlertDialog.Builder(getActivity())
                                        .setTitle("確認")
                                        .setIcon(android.R.drawable.ic_dialog_alert)
                                        .setMessage("認証情報を削除してもよろしいですか?")
                                        .setPositiveButton("OK", (dialog1, which1) -> {
                                            App.getInstance(requireContext()).getAccountManager().deleteUser(dataList.get(position).InternalId);
                                            createList();
                                        })
                                        .setNegativeButton("キャンセル", (dialog1, which1) -> {
                                        })
                                        .create();
                                alertDialog.show();
                                break;
                        }
                    });
            builder.create().show();
        }

        @Override
        public void onColorPicked(int color, String tag) {
            long id = Long.valueOf(tag);
            App.getInstance(requireContext()).getAccountManager().setUserColor(id, color);
            createList();
        }

        private class Adapter extends ArrayAdapter<AuthUserRecord> {

            private LayoutInflater inflater;

            public Adapter(Context context, List<AuthUserRecord> objects) {
                super(context, 0, objects);
                inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View v = convertView;
                ViewHolder vh;

                if (v == null) {
                    v = inflater.inflate(R.layout.row_account, parent, false);
                    vh = new ViewHolder();
                    vh.ivIcon = (ImageView) v.findViewById(R.id.user_icon);
                    vh.tvName = (TextView) v.findViewById(R.id.user_name);
                    vh.tvScreenName = (TextView) v.findViewById(R.id.user_sn);
                    vh.checkBox = (CheckBox) v.findViewById(R.id.user_check);
                    vh.ivColor = (ImageView) v.findViewById(R.id.user_color);
                    v.setTag(vh);
                }
                else {
                    vh = (ViewHolder) v.getTag();
                }

                AuthUserRecord d = getItem(position);
                if (d != null) {
                    vh.tvName.setText(d.Name);
                    vh.tvScreenName.setText("@" + d.ScreenName);
                    vh.ivIcon.setImageResource(R.drawable.yukatterload);
                    ImageLoaderTask.loadProfileIcon(getContext(), vh.ivIcon, d.ProfileImageUrl);
                    vh.checkBox.setChecked(d.isPrimary);
                    vh.ivColor.setBackgroundColor(d.AccountColor);
                }

                return v;
            }

            private class ViewHolder {
                ImageView ivIcon;
                TextView tvScreenName;
                TextView tvName;
                CheckBox checkBox;
                ImageView ivColor;
            }
        }
    }
}
