package shibafu.yukari.common;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.List;

import shibafu.yukari.R;
import twitter4j.Status;

/**
 * Created by Shibafu on 13/08/01.
 */
public class TweetAdapterWrap {
    private Context context;
    private List<Status> statuses;
    private TweetAdapter adapter;

    public TweetAdapterWrap(Context context, List<Status> statuses) {
        this.context = context;
        this.statuses = statuses;
        adapter = new TweetAdapter();
    }

    public TweetAdapter getAdapter() {
        return adapter;
    }

    public void notifyDataSetChanged() {
        adapter.notifyDataSetChanged();
    }

    private class TweetAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return statuses.size();
        }

        @Override
        public Object getItem(int position) {
            return statuses.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;

            if (v == null) {
                LayoutInflater inflater = LayoutInflater.from(context);
                v = inflater.inflate(R.layout.raw_tweet, null);
            }

            Status st = (Status) getItem(position);
            if (st != null) {
                TextView tvName = (TextView) v.findViewById(R.id.tweet_name);
                tvName.setText("@" + st.getUser().getScreenName() + " / " + st.getUser().getName());
                TextView tvText = (TextView) v.findViewById(R.id.tweet_text);
                tvText.setText(st.getText());
            }

            return v;
        }
    }
}
