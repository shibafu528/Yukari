package shibafu.yukari.common;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.preference.PreferenceManager;
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
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import shibafu.yukari.R;
import shibafu.yukari.media.LinkMedia;
import shibafu.yukari.twitter.AuthUserRecord;
import shibafu.yukari.twitter.PreformedStatus;
import twitter4j.UserMentionEntity;

/**
 * Created by Shibafu on 13/08/01.
 */
public class TweetAdapterWrap {
    private Context context;
    private SharedPreferences preferences;
    private List<AuthUserRecord> userRecords;
    private List<PreformedStatus> statuses;
    private TweetAdapter adapter;
    private final static SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.JAPAN);

    public final static int CONFIG_SHOW_THUMBNAIL    = 0x001; //サムネイルを表示
    public final static int CONFIG_DISABLE_BGCOLOR   = 0x002; //ツイートに応じたBGカラーを適用しない
    public final static int CONFIG_DISABLE_FONTCOLOR = 0x004; //フォントカラーを適用しない
    public static final int CONFIG_OMISSION_AFTER_4  = 0x010; //4行目以降を省略
    public static final int CONFIG_OMISSION_AFTER_8  = 0x020; //8行目以降を省略
    public static final int CONFIG_OMISSION_RETURNS  = 0x040; //単行表示

    public static final int MODE_DEFAULT = 0;
    public static final int MODE_DETAIL  = 1; //サムネイル表示強制
    public static final int MODE_PREVIEW = 2; //サムネイル非表示強制、モノクロ

    public TweetAdapterWrap(Context context, AuthUserRecord userRecord, List<PreformedStatus> statuses) {
        this.context = context;
        this.userRecords = new ArrayList<AuthUserRecord>();
        userRecords.add(userRecord);
        this.statuses = statuses;
        preferences = PreferenceManager.getDefaultSharedPreferences(context);
        adapter = new TweetAdapter();
    }

    public TweetAdapterWrap(Context context, List<AuthUserRecord> userRecords, List<PreformedStatus> statuses) {
        this.context = context;
        this.userRecords = userRecords;
        this.statuses = statuses;
        preferences = PreferenceManager.getDefaultSharedPreferences(context);
        adapter = new TweetAdapter();
    }

    public TweetAdapter getAdapter() {
        return adapter;
    }

    public void notifyDataSetChanged() {
        adapter.notifyDataSetChanged();
    }

    public static View setStatusToView(Context context, View v,
                                       PreformedStatus st, List<AuthUserRecord> userRecords,
                                       SharedPreferences preferences, int mode) {
        //ViewHolderを取得もしくは新規作成
        TweetViewHolder viewHolder = (TweetViewHolder) v.getTag(R.string.key_viewholder);
        if (viewHolder == null) {
            viewHolder = new TweetViewHolder(v);
            v.setTag(R.string.key_viewholder, viewHolder);
        }

        int multilineMode = Integer.valueOf(preferences.getString("pref_mode_multiline", "0"));
        String fontSizeStr = preferences.getString("pref_font_timeline", "14");
        if (fontSizeStr.equals("")) {
            fontSizeStr = "14";
        }
        int fontSize = Integer.valueOf(fontSizeStr);

        viewHolder.tvName.setText("@" + st.getUser().getScreenName() + " / " + st.getUser().getName());
        viewHolder.tvName.setTypeface(FontAsset.getInstance(context).getFont());
        viewHolder.tvName.setTextSize(fontSize);

        viewHolder.tvText.setTypeface(FontAsset.getInstance(context).getFont());
        viewHolder.tvText.setTextSize(fontSize);
        String text = st.isRetweet()? st.getRetweetedStatus().getText() : st.getText();
        if ((multilineMode & CONFIG_OMISSION_RETURNS) == CONFIG_OMISSION_RETURNS) {
            text = text.replace('\n', ' ');
        }
        if ((multilineMode & 0x30) > 0) {
            String[] lines = text.split("\n");
            text = "";
            int limit = (multilineMode & CONFIG_OMISSION_AFTER_4) == CONFIG_OMISSION_AFTER_4? 3 : 7;
            int i;
            for (i = 0; i < lines.length && i < limit; ++i) {
                if (i > 0) text += "\n";
                text += lines[i];
            }
            if (i >= limit && i <= lines.length) {
                text += " ...";
            }
        }
        viewHolder.tvText.setText(text);

        String imageUrl = st.getUser().getBiggerProfileImageURL();
        if (st.isRetweet()) {
            imageUrl = st.getRetweetedStatus().getUser().getBiggerProfileImageURL();
        }
        if (viewHolder.ivIcon.getTag() == null || !viewHolder.ivIcon.getTag().equals(imageUrl)) {
            viewHolder.ivIcon.setTag(imageUrl);
            IconLoaderTask loaderTask = new IconLoaderTask(context, viewHolder.ivIcon);
            loaderTask.executeIf(imageUrl);
        }

        viewHolder.tvTimestamp.setTypeface(FontAsset.getInstance(context).getFont());
        viewHolder.tvTimestamp.setTextSize(fontSize * 0.8f);
        String timestamp = sdf.format(st.getCreatedAt()) + " via " + st.getSource();

        if (st.getUser().isProtected()) {
            viewHolder.ivProtected.setVisibility(View.VISIBLE);
        }
        else {
            viewHolder.ivProtected.setVisibility(View.INVISIBLE);
        }

        viewHolder.llAttach.removeAllViews();

        if ((preferences.getBoolean("pref_prev_enable", true) && mode != MODE_PREVIEW) || mode == MODE_DETAIL) {
            boolean hidden = false;

            int selectedFlags = preferences.getInt("pref_prev_time", 0);
            if (selectedFlags != 0) {
                boolean[] selectedStates = new boolean[24];
                for (int i = 0; i < 24; ++i) {
                    selectedStates[i] = (selectedFlags & 0x01) == 1;
                    selectedFlags >>>= 1;
                }
                Calendar calendar = Calendar.getInstance();
                hidden = selectedStates[calendar.get(Calendar.HOUR_OF_DAY)];
            }
            if (!hidden) {
                List<LinkMedia> mediaList = st.getMediaLinkList();
                if (mediaList.size() > 0) {
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(140, 140, 1);
                    for (LinkMedia media : mediaList) {
                        SmartImageView siv = new SmartImageView(context);
                        siv.setImageResource(R.drawable.yukatterload);
                        siv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                        siv.setImageUrl(media.getThumbURL());
                        viewHolder.llAttach.addView(siv, lp);
                    }
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

        if (mode != MODE_PREVIEW) {
            if (st.isRetweet()) {
                timestamp = "RT by @" + st.getUser().getScreenName() + "\n" +
                        sdf.format(st.getRetweetedStatus().getCreatedAt()) + " via " + st.getRetweetedStatus().getSource();
                viewHolder.tvName.setText("@" + st.getRetweetedStatus().getUser().getScreenName() + " / " + st.getRetweetedStatus().getUser().getName());
                v.setBackgroundResource(R.drawable.selector_tweet_retweet_background);

                if (st.getRetweetedStatus().getUser().isProtected()) {
                    viewHolder.ivProtected.setVisibility(View.VISIBLE);
                }
                else {
                    viewHolder.ivProtected.setVisibility(View.INVISIBLE);
                }

                viewHolder.ivRetweeterIcon.setVisibility(View.VISIBLE);
                viewHolder.ivRetweeterIcon.setTag(st.getUser().getProfileImageURLHttps());
                IconLoaderTask task = new IconLoaderTask(context, viewHolder.ivRetweeterIcon);
                task.executeIf(st.getUser().getProfileImageURLHttps());
            }
            else if (isMention) {
                v.setBackgroundResource(R.drawable.selector_tweet_mention_background);
            }
            else if (isMe) {
                v.setBackgroundResource(R.drawable.selector_tweet_own_background);
            }
            else {
                v.setBackgroundResource(R.drawable.selector_tweet_normal_background);
            }

            if (!st.isRetweet()) {
                viewHolder.ivRetweeterIcon.setVisibility(View.INVISIBLE);
                viewHolder.ivRetweeterIcon.setImageDrawable(new ColorDrawable(Color.TRANSPARENT));
            }
        }
        viewHolder.tvTimestamp.setText(timestamp);

        if (mode == MODE_PREVIEW) {
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
                v = setStatusToView(context, v, st, userRecords, preferences, MODE_DEFAULT);
            }

            return v;
        }
    }

    private static class TweetViewHolder {
        TextView tvName;
        TextView tvText;
        ImageView ivIcon, ivRetweeterIcon;
        ImageView ivProtected;
        TextView tvTimestamp;
        LinearLayout llAttach;

        public TweetViewHolder(View v) {
            tvName = (TextView) v.findViewById(R.id.tweet_name);
            tvText = (TextView) v.findViewById(R.id.tweet_text);
            ivIcon = (ImageView)v.findViewById(R.id.tweet_icon);
            ivRetweeterIcon = (ImageView) v.findViewById(R.id.tweet_retweeter);
            ivProtected = (ImageView) v.findViewById(R.id.tweet_protected);
            tvTimestamp = (TextView)v.findViewById(R.id.tweet_timestamp);
            llAttach = (LinearLayout) v.findViewById(R.id.tweet_attach);
        }

    }
}
