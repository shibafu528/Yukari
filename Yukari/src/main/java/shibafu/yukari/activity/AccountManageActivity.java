package shibafu.yukari.activity;

import android.app.Activity;
import android.support.v7.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
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

import java.util.ArrayList;
import java.util.List;

import shibafu.yukari.R;
import shibafu.yukari.activity.base.ActionBarYukariBase;
import shibafu.yukari.common.bitmapcache.ImageLoaderTask;
import shibafu.yukari.fragment.ColorPickerDialogFragment;
import shibafu.yukari.service.TwitterServiceDelegate;
import shibafu.yukari.twitter.AuthUserRecord;

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
        public void onAttach(Activity activity) {
            super.onAttach(activity);
            delegate = (TwitterServiceDelegate) getActivity();
        }

        public void createList() {
            dataList.clear();
            dataList.addAll(delegate.getTwitterService().getUsers());
            adapter = new Adapter(getActivity(), dataList);
            setListAdapter(adapter);
        }

        @Override
        public void onListItemClick(ListView l, View v, final int position, long id) {
            super.onListItemClick(l, v, position, id);

            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                    .setTitle("メニュー")
                    .setItems(new String[]{"プライマリに設定", "アカウントカラーを設定", "認証情報を削除"}, (dialog, which) -> {
                        switch (which) {
                            case 0:
                                delegate.getTwitterService().setPrimaryUser(dataList.get(position).NumericId);
                                createList();
                                break;
                            case 1:
                                ColorPickerDialogFragment dialogFragment
                                        = ColorPickerDialogFragment.newInstance(
                                        dataList.get(position).AccountColor,
                                        String.valueOf(dataList.get(position).NumericId));
                                dialogFragment.setTargetFragment(AccountListFragment.this, 1);
                                dialogFragment.show(getFragmentManager(), "color");
                                break;
                            case 2:
                                AlertDialog alertDialog = new AlertDialog.Builder(getActivity())
                                        .setTitle("確認")
                                        .setIcon(android.R.drawable.ic_dialog_alert)
                                        .setMessage("認証情報を削除してもよろしいですか?")
                                        .setPositiveButton("OK", (dialog1, which1) -> {
                                            delegate.getTwitterService().deleteUser(dataList.get(position).NumericId);
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
            delegate.getTwitterService().setUserColor(id, color);
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
