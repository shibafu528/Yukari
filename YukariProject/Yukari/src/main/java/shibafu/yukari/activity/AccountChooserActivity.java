package shibafu.yukari.activity;

import android.app.ListActivity;
import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.TextView;

import com.loopj.android.image.SmartImageView;

import java.util.ArrayList;
import java.util.List;

import shibafu.yukari.R;
import shibafu.yukari.service.TwitterService;
import shibafu.yukari.twitter.AuthUserRecord;

/**
 * Created by Shibafu on 13/12/16.
 */
public class AccountChooserActivity extends ListActivity {

    public static final String EXTRA_MULTIPLE_CHOOSE = "multiple_choose";

    private TwitterService service;
    private boolean serviceBound = false;

    private boolean isMultipleChoose = false;

    private Adapter adapter;
    private ArrayList<Data> dataList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    private void createList() {
        List<AuthUserRecord> users = service.getUsers();
        //TODO: ユーザをリストアップする処理を書く
    }

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            TwitterService.TweetReceiverBinder binder = (TwitterService.TweetReceiverBinder) service;
            AccountChooserActivity.this.service = binder.getService();
            createList();
            serviceBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
        }
    };

    private class Data {
        long id;
        String name;
        String sn;
        String imageURL;
        boolean checked;

        private Data(long id, String name, String sn, String imageURL, boolean checked) {
            this.id = id;
            this.name = name;
            this.sn = sn;
            this.imageURL = imageURL;
            this.checked = checked;
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
                v = inflater.inflate(R.layout.row_user, parent, false);
                vh = new ViewHolder();
                vh.ivIcon = (SmartImageView) v.findViewById(R.id.user_icon);
                vh.tvName = (TextView) v.findViewById(R.id.user_name);
                vh.tvScreenName = (TextView) v.findViewById(R.id.user_sn);
                vh.checkBox = (CheckBox) v.findViewById(R.id.user_check);
                if (!isMultipleChoose) {
                    vh.checkBox.setVisibility(View.GONE);
                }
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
                vh.ivIcon.setImageUrl(d.imageURL);
                vh.checkBox.setChecked(d.checked);
            }

            return v;
        }

        private class ViewHolder {
            SmartImageView ivIcon;
            TextView tvScreenName;
            TextView tvName;
            CheckBox checkBox;
        }
    }
}
