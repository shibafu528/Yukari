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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.loopj.android.image.SmartImageView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import shibafu.yukari.R;
import shibafu.yukari.common.bitmapcache.IconLoaderTask;
import shibafu.yukari.media.LinkMedia;
import shibafu.yukari.media.Meshi;
import shibafu.yukari.twitter.AuthUserRecord;
import shibafu.yukari.twitter.PreformedStatus;
import shibafu.yukari.twitter.TweetCommon;
import shibafu.yukari.twitter.TweetCommonDelegate;
import twitter4j.DirectMessage;
import twitter4j.TwitterResponse;
import twitter4j.User;

/**
 * Created by Shibafu on 13/08/01.
 */
public class TweetAdapterWrap {
    private List<? extends TwitterResponse> statuses;
    private ViewConverter converter;
    private TweetAdapter adapter;
    private LayoutInflater inflater;

    public TweetAdapterWrap(Context context,
                            List<AuthUserRecord> userRecords,
                            List<? extends TwitterResponse> statuses,
                            Class<? extends TwitterResponse> contentClass) {
        this.statuses = statuses;
        adapter = new TweetAdapter();
        inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        converter = ViewConverter.newInstance(
                context,
                userRecords,
                PreferenceManager.getDefaultSharedPreferences(context),
                contentClass);
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
        public TwitterResponse getItem(int position) {
            return statuses.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = inflater.inflate(R.layout.row_tweet, null);
            }

            TwitterResponse item = getItem(position);
            if (item != null) {
                convertView = converter.convertView(convertView, item, ViewConverter.MODE_DEFAULT);
            }

            return convertView;
        }
    }

    private static class TweetViewHolder {
        TextView tvName;
        TextView tvText;
        ImageView ivIcon, ivRetweeterIcon;
        ImageView ivProtected;
        TextView tvTimestamp;
        LinearLayout llAttach;
        TextView tvReceived;

        public TweetViewHolder(View v) {
            tvName = (TextView) v.findViewById(R.id.tweet_name);
            tvText = (TextView) v.findViewById(R.id.tweet_text);
            ivIcon = (ImageView)v.findViewById(R.id.tweet_icon);
            ivRetweeterIcon = (ImageView) v.findViewById(R.id.tweet_retweeter);
            ivProtected = (ImageView) v.findViewById(R.id.tweet_protected);
            tvTimestamp = (TextView)v.findViewById(R.id.tweet_timestamp);
            llAttach = (LinearLayout) v.findViewById(R.id.tweet_attach);
            tvReceived = (TextView) v.findViewById(R.id.tweet_receive);
        }
    }

    public static abstract class ViewConverter {
        public final static int CONFIG_SHOW_THUMBNAIL    = 0x001; //サムネイルを表示
        public final static int CONFIG_DISABLE_BGCOLOR   = 0x002; //ツイートに応じたBGカラーを適用しない
        public final static int CONFIG_DISABLE_FONTCOLOR = 0x004; //フォントカラーを適用しない
        public static final int CONFIG_OMISSION_AFTER_4  = 0x010; //4行目以降を省略
        public static final int CONFIG_OMISSION_AFTER_8  = 0x020; //8行目以降を省略
        public static final int CONFIG_OMISSION_RETURNS  = 0x040; //単行表示

        public static final int MODE_DEFAULT = 0;
        public static final int MODE_DETAIL  = 1; //サムネイル表示強制
        public static final int MODE_PREVIEW = 2; //サムネイル非表示強制、モノクロ

        private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.JAPAN);

        private Context context;
        private List<AuthUserRecord> userRecords;
        private SharedPreferences preferences;
        private TweetCommonDelegate delegate;

        public static ViewConverter newInstance(
                Context context,
                List<AuthUserRecord> userRecords,
                SharedPreferences sharedPreferences,
                Class<? extends TwitterResponse> cls) {
            try {
                if (PreformedStatus.class.isAssignableFrom(cls)) {
                    return new StatusViewConverter(context, userRecords, sharedPreferences);
                }
                else if (DirectMessage.class.isAssignableFrom(cls)) {
                    return new MessageViewConverter(context, userRecords, sharedPreferences);
                }
            } catch (IllegalAccessException | InstantiationException e) {
                throw new RuntimeException(e);
            }
            throw new ClassCastException("対応されているクラスではありません.");
        }

        protected ViewConverter(Context context,
                                List<AuthUserRecord> userRecords,
                                SharedPreferences preferences,
                                Class<? extends TweetCommonDelegate> delegateClass)
                throws IllegalAccessException, InstantiationException {
            this.context = context;
            this.userRecords = userRecords;
            this.preferences = preferences;
            this.delegate = delegateClass.newInstance();
        }

        protected Context getContext() {
            return context;
        }

        protected List<AuthUserRecord> getUserRecords() {
            return userRecords;
        }

        protected SharedPreferences getPreferences() {
            return preferences;
        }

        protected SimpleDateFormat getDateFormat() {
            return sdf;
        }

        public View convertView(View v, TwitterResponse content, int mode) {
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

            User u = delegate.getUser(content);

            viewHolder.tvName.setTypeface(FontAsset.getInstance(context).getFont());
            viewHolder.tvName.setText("@" + u.getScreenName() + " / " + u.getName());
            viewHolder.tvName.setTextSize(fontSize);

            viewHolder.tvText.setTypeface(FontAsset.getInstance(context).getFont());
            viewHolder.tvText.setTextSize(fontSize);
            String text = delegate.getText(content);
            if (mode == MODE_DEFAULT) {
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
            }
            viewHolder.tvText.setText(text);

            String imageUrl = u.getBiggerProfileImageURL();
            if (viewHolder.ivIcon.getTag() == null || !viewHolder.ivIcon.getTag().equals(imageUrl)) {
                viewHolder.ivIcon.setTag(imageUrl);
                IconLoaderTask loaderTask = new IconLoaderTask(context, viewHolder.ivIcon);
                loaderTask.executeIf(imageUrl);
            }

            viewHolder.tvTimestamp.setTypeface(FontAsset.getInstance(context).getFont());
            viewHolder.tvTimestamp.setTextSize(fontSize * 0.8f);

            if (delegate.getUser(content).isProtected()) {
                viewHolder.ivProtected.setVisibility(View.VISIBLE);
            }
            else {
                viewHolder.ivProtected.setVisibility(View.INVISIBLE);
            }

            int statusRelation = (userRecords != null) ?
                    delegate.getStatusRelation(userRecords, content) : 0;
            if (mode != MODE_PREVIEW) {
                switch (statusRelation) {
                    case TweetCommonDelegate.REL_MENTION:
                        v.setBackgroundResource(R.drawable.selector_tweet_mention_background);
                        break;
                    case TweetCommonDelegate.REL_OWN:
                        v.setBackgroundResource(R.drawable.selector_tweet_own_background);
                        break;
                    default:
                        v.setBackgroundResource(R.drawable.selector_tweet_normal_background);
                        break;
                }
            }
            viewHolder.tvTimestamp.setText(getDateFormat().format(delegate.getCreatedAt(content)) + " via " + delegate.getSource(content));

            if (mode == MODE_PREVIEW) {
                viewHolder.tvName.setTextColor(Color.BLACK);
                viewHolder.tvTimestamp.setTextColor(Color.BLACK);
            }

            if (preferences.getBoolean("pref_show_received", false)) {
                viewHolder.tvReceived.setVisibility(View.VISIBLE);
                viewHolder.tvReceived.setTypeface(FontAsset.getInstance(context).getFont());
                viewHolder.tvReceived.setTextSize(fontSize * 0.8f);
                viewHolder.tvReceived.setText(String.format("Received from @%s", delegate.getRecipientScreenName(content)));
            }
            else {
                viewHolder.tvReceived.setVisibility(View.GONE);
            }

            return convertViewAbs(v, viewHolder, content, mode);
        }

        protected abstract View convertViewAbs(View v, TweetViewHolder viewHolder, TwitterResponse content, int mode);
    }

    private static class StatusViewConverter extends ViewConverter {

        protected StatusViewConverter(Context context,
                                      List<AuthUserRecord> userRecords,
                                      SharedPreferences preferences)
                throws IllegalAccessException, InstantiationException {
            super(context, userRecords, preferences, TweetCommon.getImplementClass(PreformedStatus.class));
        }

        @Override
        protected View convertViewAbs(View v, TweetViewHolder viewHolder, TwitterResponse content, int mode) {
            viewHolder.llAttach.removeAllViews();
            PreformedStatus st = (PreformedStatus) content;

            if ((getPreferences().getBoolean("pref_prev_enable", true) && mode != MODE_PREVIEW) || mode == MODE_DETAIL) {
                boolean hidden = false;

                int selectedFlags = getPreferences().getInt("pref_prev_time", 0);
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
                            if (!getPreferences().getBoolean("pref_prev_mstrin", true) && media instanceof Meshi) continue;
                            SmartImageView siv = new SmartImageView(getContext());
                            siv.setImageResource(R.drawable.yukatterload);
                            siv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                            siv.setImageUrl(media.getThumbURL());
                            viewHolder.llAttach.addView(siv, lp);
                        }
                    }
                }
            }

            if (mode != MODE_PREVIEW) {
                if (st.isRetweet()) {
                    String timestamp = "RT by @" + st.getUser().getScreenName() + "\n" +
                            getDateFormat().format(st.getRetweetedStatus().getCreatedAt()) + " via " + st.getRetweetedStatus().getSource();
                    viewHolder.tvTimestamp.setText(timestamp);
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
                    IconLoaderTask task = new IconLoaderTask(getContext(), viewHolder.ivRetweeterIcon);
                    task.executeIf(st.getUser().getProfileImageURLHttps());
                }
                else {
                    viewHolder.ivRetweeterIcon.setVisibility(View.INVISIBLE);
                    viewHolder.ivRetweeterIcon.setImageDrawable(new ColorDrawable(Color.TRANSPARENT));
                }
            }
            return v;
        }
    }

    private static class MessageViewConverter extends ViewConverter {

        protected MessageViewConverter(Context context,
                                       List<AuthUserRecord> userRecords,
                                       SharedPreferences preferences)
                throws IllegalAccessException, InstantiationException {
            super(context, userRecords, preferences, TweetCommon.getImplementClass(DirectMessage.class));
        }

        @Override
        protected View convertViewAbs(View v, TweetViewHolder viewHolder, TwitterResponse content, int mode) {
            return v;
        }
    }
}
