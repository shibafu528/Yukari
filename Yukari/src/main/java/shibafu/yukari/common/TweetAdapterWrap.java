package shibafu.yukari.common;

import android.content.Context;
import android.graphics.Color;
import android.os.AsyncTask;
import android.util.Log;
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
import shibafu.yukari.twitter.PreformedStatus;
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
    private List<PreformedStatus> statuses;
    private TweetAdapter adapter;
    private final static SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.JAPAN);

    public final static int CONFIG_SHOW_THUMBNAIL    = 0x01;
    public final static int CONFIG_DISABLE_BGCOLOR   = 0x02;
    public final static int CONFIG_DISABLE_FONTCOLOR = 0x04;

    public TweetAdapterWrap(Context context, AuthUserRecord userRecord, List<PreformedStatus> statuses) {
        this.context = context;
        this.userRecords = new ArrayList<AuthUserRecord>();
        userRecords.add(userRecord);
        this.statuses = statuses;
        adapter = new TweetAdapter();
    }

    public TweetAdapterWrap(Context context, List<AuthUserRecord> userRecords, List<PreformedStatus> statuses) {
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

    public static View setStatusToView(Context context, View v, Status st, List<AuthUserRecord> userRecords, int config) {
        return setStatusToView(context, v, new PreformedStatus(st, null), userRecords, config);
    }

    public static View setStatusToView(Context context, View v, PreformedStatus st, List<AuthUserRecord> userRecords, int config) {
        //ViewHolderを取得もしくは新規作成
        TweetViewHolder viewHolder = (TweetViewHolder) v.getTag(R.string.key_viewholder);
        if (viewHolder == null) {
            viewHolder = new TweetViewHolder(v);
            v.setTag(R.string.key_viewholder, viewHolder);
        }

        viewHolder.tvName.setText("@" + st.getUser().getScreenName() + " / " + st.getUser().getName());
        viewHolder.tvName.setTypeface(FontAsset.getInstance(context).getFont());

        viewHolder.tvText.setTypeface(FontAsset.getInstance(context).getFont());
        viewHolder.tvText.setText(st.getText());

        String imageUrl = st.getUser().getBiggerProfileImageURL();
        if (st.isRetweet()) {
            imageUrl = st.getRetweetedStatus().getUser().getBiggerProfileImageURL();
        }
        if (viewHolder.ivIcon.getTag() == null || !viewHolder.ivIcon.getTag().equals(imageUrl)) {
            Log.d("TweetAdapterWrap", "Loading Icon: " + imageUrl + " (before: " + viewHolder.ivIcon.getTag() + ")");
            viewHolder.ivIcon.setImageResource(R.drawable.yukatterload);
            viewHolder.ivIcon.setTag(imageUrl);
            IconLoaderTask loaderTask = new IconLoaderTask(context, viewHolder.ivIcon);
            loaderTask.executeIf(imageUrl);
        }

        viewHolder.tvTimestamp.setTypeface(FontAsset.getInstance(context).getFont());
        String timestamp = sdf.format(st.getCreatedAt()) + " via " + st.getSource();

        viewHolder.llAttach.removeAllViews();

        if ((config & CONFIG_SHOW_THUMBNAIL) == CONFIG_SHOW_THUMBNAIL) {
            List<String> mediaList = st.getMediaLinkList();
            int frameWidth = viewHolder.llAttach.getWidth();
            if (mediaList.size() > 0) {
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(frameWidth / mediaList.size(), 140);
                for (String mediaURL : mediaList) {
                    SmartImageView siv = new SmartImageView(context);
                    siv.setImageResource(R.drawable.yukatterload);
                    siv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    siv.setImageUrl(mediaURL);
                    viewHolder.llAttach.addView(siv, lp);
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
                timestamp = "RT by @" + st.getUser().getScreenName() + "\n" +
                        sdf.format(st.getRetweetedStatus().getCreatedAt()) + " via " + st.getRetweetedStatus().getSource();
                viewHolder.tvName.setText("@" + st.getRetweetedStatus().getUser().getScreenName() + " / " + st.getRetweetedStatus().getUser().getName());
                viewHolder.tvText.setText(st.getRetweetedStatus().getText());
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
        viewHolder.tvTimestamp.setText(timestamp);

        if ((config & CONFIG_DISABLE_FONTCOLOR) == CONFIG_DISABLE_FONTCOLOR) {
            viewHolder.tvName.setTextColor(Color.BLACK);
            viewHolder.tvTimestamp.setTextColor(Color.BLACK);
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

            PreformedStatus st = (PreformedStatus) getItem(position);
            if (st != null) {
                v = setStatusToView(context, v, st, userRecords, CONFIG_SHOW_THUMBNAIL);
            }

            return v;
        }
    }

    private static class TweetViewHolder {
        TextView tvName;
        TextView tvText;
        ImageView ivIcon;
        TextView tvTimestamp;
        LinearLayout llAttach;

        public TweetViewHolder(View v) {
            tvName = (TextView) v.findViewById(R.id.tweet_name);
            tvText = (TextView) v.findViewById(R.id.tweet_text);
            ivIcon = (ImageView)v.findViewById(R.id.tweet_icon);
            tvTimestamp = (TextView)v.findViewById(R.id.tweet_timestamp);
            llAttach = (LinearLayout) v.findViewById(R.id.tweet_attach);
        }

    }
}
