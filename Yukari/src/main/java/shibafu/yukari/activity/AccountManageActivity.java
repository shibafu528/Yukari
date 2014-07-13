package shibafu.yukari.activity;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
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
import shibafu.yukari.activity.base.ListYukariBase;
import shibafu.yukari.common.bitmapcache.ImageLoaderTask;
import shibafu.yukari.twitter.AuthUserRecord;

/**
 * Created by Shibafu on 13/12/21.
 */
public class AccountManageActivity extends ListYukariBase {

    private Adapter adapter;
    private List<Data> dataList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.accountmanage, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_add:
                startActivity(new Intent(this, OAuthActivity.class));
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void createList() {
        List<AuthUserRecord> users = getTwitterService().getUsers();
        dataList.clear();
        for (AuthUserRecord userRecord : users) {
            dataList.add(new Data(userRecord.NumericId, userRecord.Name, userRecord.ScreenName, userRecord.ProfileImageUrl, userRecord.isPrimary));
        }
        adapter = new Adapter(AccountManageActivity.this, dataList);
        setListAdapter(adapter);
    }

    @Override
    protected void onListItemClick(ListView l, View v, final int position, long id) {
        super.onListItemClick(l, v, position, id);

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("メニュー")
                .setItems(new String[]{"プライマリに設定", "認証情報を削除"}, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            getTwitterService().setPrimaryUser(dataList.get(position).id);
                            createList();
                        }
                        else {
                            AlertDialog alertDialog = new AlertDialog.Builder(AccountManageActivity.this)
                                    .setTitle("確認")
                                    .setIcon(android.R.drawable.ic_dialog_alert)
                                    .setMessage("認証情報を削除してもよろしいですか?")
                                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            getTwitterService().deleteUser(dataList.get(position).id);
                                            createList();
                                        }
                                    })
                                    .setNegativeButton("キャンセル", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                        }
                                    })
                                    .create();
                            alertDialog.show();
                        }
                    }
                });
        builder.create().show();
    }

    @Override
    public void onServiceConnected() {
        createList();
    }

    @Override
    public void onServiceDisconnected() {

    }

    private class Data {
        long id;
        String name;
        String sn;
        String imageURL;
        boolean isPrimary;

        private Data(long id, String name, String sn, String imageURL, boolean isPrimary) {
            this.id = id;
            this.name = name;
            this.sn = sn;
            this.imageURL = imageURL;
            this.isPrimary = isPrimary;
        }
    }

    private class Adapter extends ArrayAdapter<Data> {

        private LayoutInflater inflater;

        public Adapter(Context context, List<Data> objects) {
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
                v.setTag(vh);
            }
            else {
                vh = (ViewHolder) v.getTag();
            }

            Data d = getItem(position);
            if (d != null) {
                vh.tvName.setText(d.name);
                vh.tvScreenName.setText("@" + d.sn);
                vh.ivIcon.setImageResource(R.drawable.yukatterload);
                ImageLoaderTask.loadProfileIcon(getApplicationContext(), vh.ivIcon, d.imageURL);
                vh.checkBox.setChecked(d.isPrimary);
            }

            return v;
        }

        private class ViewHolder {
            ImageView ivIcon;
            TextView tvScreenName;
            TextView tvName;
            CheckBox checkBox;
        }
    }
}
