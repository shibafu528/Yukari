package shibafu.yukari.common;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.loopj.android.image.SmartImageView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import shibafu.util.MorseCodec;
import shibafu.util.TweetImageUrl;
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

    public final static int CONFIG_SHOW_THUMBNAIL    = 0x01;
    public final static int CONFIG_DISABLE_BGCOLOR   = 0x02;
    public final static int CONFIG_DISABLE_FONTCOLOR = 0x04;

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
            text = text.replace(e.getURL(), e.getMediaURL());
        }
        return text;
    }

    public static View setStatusToView(Context context, View v, Status st, List<AuthUserRecord> userRecords, int config) {
        TextView tvName = (TextView) v.findViewById(R.id.tweet_name);
        tvName.setText("@" + st.getUser().getScreenName() + " / " + st.getUser().getName());
        tvName.setTypeface(FontAsset.getInstance(context).getFont());

        TextView tvText = (TextView) v.findViewById(R.id.tweet_text);
        tvText.setTypeface(FontAsset.getInstance(context).getFont());
        String text = replaceAllEntities(st);
        text = MorseCodec.decode(text);
        tvText.setText(text);

        SmartImageView ivIcon = (SmartImageView)v.findViewById(R.id.tweet_icon);
        ivIcon.setImageResource(R.drawable.yukatterload);
        ivIcon.setImageUrl(st.getUser().getProfileImageURL());

        TextView tvTimestamp = (TextView)v.findViewById(R.id.tweet_timestamp);
        tvTimestamp.setTypeface(FontAsset.getInstance(context).getFont());
        Matcher matcher = VIA_PATTERN.matcher(st.getSource());
        String via;
        if (matcher.find()) {
            via = matcher.group(1);
        }
        else {
            via = st.getSource();
        }
        String timestamp = sdf.format(st.getCreatedAt()) + " via " + via;

        LinearLayout llAttach = (LinearLayout) v.findViewById(R.id.tweet_attach);
        llAttach.removeAllViews();

        if ((config & CONFIG_SHOW_THUMBNAIL) == CONFIG_SHOW_THUMBNAIL) {
            ArrayList<String> mediaList = new ArrayList<String>();
            for (URLEntity urlEntity : st.getURLEntities()) {
                String expanded = TweetImageUrl.getFullImageUrl(urlEntity.getExpandedURL());
                if (expanded != null) {
                    mediaList.add(expanded);
                }
            }
            for (MediaEntity mediaEntity : st.getMediaEntities()) {
                mediaList.add(mediaEntity.getMediaURL());
            }
            int frameWidth = llAttach.getWidth();
            if (mediaList.size() > 0) {
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(frameWidth / mediaList.size(), 140);
                for (String mediaURL : mediaList) {
                    SmartImageView siv = new SmartImageView(context);
                    siv.setImageResource(R.drawable.yukatterload);
                    siv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    siv.setImageUrl(mediaURL);
                    llAttach.addView(siv, lp);
                }
            }
        }

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

        if ((config & CONFIG_DISABLE_BGCOLOR) != CONFIG_DISABLE_BGCOLOR) {
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
        }
        tvTimestamp.setText(timestamp);

        if ((config & CONFIG_DISABLE_FONTCOLOR) == CONFIG_DISABLE_FONTCOLOR) {
            tvName.setTextColor(Color.BLACK);
            tvTimestamp.setTextColor(Color.BLACK);
        }

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
                v = setStatusToView(context, v, st, userRecords, CONFIG_SHOW_THUMBNAIL);
            }

            return v;
        }
    }
}
