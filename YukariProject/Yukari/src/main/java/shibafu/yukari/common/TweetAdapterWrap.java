package shibafu.yukari.common;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.loopj.android.image.SmartImageView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import shibafu.util.MorseCodec;
import shibafu.yukari.R;
import shibafu.yukari.twitter.AuthUserRecord;
import twitter4j.MediaEntity;
import twitter4j.Status;
import twitter4j.URLEntity;
import twitter4j.UserMentionEntity;

/**
 * Created by Shibafu on 13/08/01.
 */
public class TweetAdapterWrap {
    private Context context;
    private List<AuthUserRecord> userRecords;
    private List<Status> statuses;
    private TweetAdapter adapter;
    private final static SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.JAPAN);
    private final static Pattern VIA_PATTERN = Pattern.compile("<a .*>(.+)</a>");
    private final static Pattern SIGNAL_PATTERN = Pattern.compile("(((−|・|－)+) ?)+");

    public TweetAdapterWrap(Context context, AuthUserRecord userRecord, List<Status> statuses) {
        this.context = context;
        this.userRecords = new ArrayList<AuthUserRecord>();
        userRecords.add(userRecord);
        this.statuses = statuses;
        adapter = new TweetAdapter();
    }

    public TweetAdapterWrap(Context context, List<AuthUserRecord> userRecords, List<Status> statuses) {
        this.context = context;
        this.userRecords = userRecords;
        this.statuses = statuses;
        adapter = new TweetAdapter();
    }

    public TweetAdapter getAdapter() {
        return adapter;
    }

    public void notifyDataSetChanged() {
        adapter.notifyDataSetChanged();
    }

    private static String replaceAllEntities(Status status) {
        String text = status.getText();
        for (URLEntity e : status.getURLEntities()) {
            text = text.replace(e.getURL(), e.getExpandedURL());
        }
        for (MediaEntity e : status.getMediaEntities()) {
            text = text.replace(e.getURL(), e.getExpandedURL());
        }
        return text;
    }

    public static View setStatusToView(Context context, View v, Status st, List<AuthUserRecord> userRecords) {
        TextView tvName = (TextView) v.findViewById(R.id.tweet_name);
        tvName.setText("@" + st.getUser().getScreenName() + " / " + st.getUser().getName());
        tvName.setTypeface(FontAsset.getInstance(context).getFont());

        TextView tvText = (TextView) v.findViewById(R.id.tweet_text);
        tvText.setTypeface(FontAsset.getInstance(context).getFont());
        String text = st.getText();
        text = MorseCodec.decode(text);
        text = replaceAllEntities(st);
        tvText.setText(text);

        SmartImageView ivIcon = (SmartImageView)v.findViewById(R.id.tweet_icon);
        ivIcon.setImageResource(R.drawable.ic_launcher);
        ivIcon.setImageUrl(st.getUser().getProfileImageURL());

        TextView tvTimestamp = (TextView)v.findViewById(R.id.tweet_timestamp);
        Matcher matcher = VIA_PATTERN.matcher(st.getSource());
        String via;
        if (matcher.find()) {
            via = matcher.group(1);
        }
        else {
            via = st.getSource();
        }
        String timestamp = sdf.format(st.getCreatedAt()) + " via " + via;

        boolean isMention = false, isMe = false;
        if (userRecords != null) {
            for (UserMentionEntity entity : st.getUserMentionEntities()) {
                for (AuthUserRecord aur : userRecords) {
                    if (aur.ScreenName.equals(entity.getScreenName())) {
                        isMention = true;
                        break;
                    }
                }
            }
            for (AuthUserRecord aur : userRecords) {
                if (aur.ScreenName.equals(st.getInReplyToScreenName())) {
                    isMention = true;
                }
                if (aur.ScreenName.equals(st.getUser().getScreenName())) {
                    isMe = true;
                }
            }
        }

        int bgColor = Color.WHITE;
        if (st.isRetweet()) {
            timestamp = "RT by @" + st.getUser().getScreenName() + "\n" + timestamp;
            tvName.setText("@" + st.getRetweetedStatus().getUser().getScreenName() + " / " + st.getRetweetedStatus().getUser().getName());
            tvText.setText(st.getRetweetedStatus().getText());
            ivIcon.setImageUrl(st.getRetweetedStatus().getUser().getProfileImageURL());
            bgColor = Color.parseColor("#C2B7FD");
        }
        else if (isMention) {
            bgColor = Color.parseColor("#EDB3DD");
        }
        else if (isMe) {
            bgColor = Color.parseColor("#EFDCFF");
        }
        v.setBackgroundColor(bgColor);
        v.setTag(bgColor);
        tvTimestamp.setText(timestamp);

        return v;
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
                LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                v = inflater.inflate(R.layout.row_tweet, null);
            }

            Status st = (Status) getItem(position);
            if (st != null) {
                v = setStatusToView(context, v, st, userRecords);
            }

            return v;
        }
    }
}
