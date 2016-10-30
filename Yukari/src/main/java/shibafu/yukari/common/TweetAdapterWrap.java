package shibafu.yukari.common;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import shibafu.yukari.R;
import shibafu.yukari.activity.PreviewActivity;
import shibafu.yukari.common.bitmapcache.BitmapCache;
import shibafu.yukari.common.bitmapcache.ImageLoaderTask;
import shibafu.yukari.database.UserExtras;
import shibafu.yukari.media.LinkMedia;
import shibafu.yukari.media.Meshi;
import shibafu.yukari.twitter.AuthUserRecord;
import shibafu.yukari.twitter.TweetCommon;
import shibafu.yukari.twitter.TweetCommonDelegate;
import shibafu.yukari.twitter.statusimpl.HistoryStatus;
import shibafu.yukari.twitter.statusimpl.LoadMarkerStatus;
import shibafu.yukari.twitter.statusimpl.PreformedStatus;
import shibafu.yukari.twitter.statusmanager.StatusManager;
import shibafu.yukari.util.AttrUtil;
import shibafu.yukari.util.StringUtil;
import twitter4j.*;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Created by Shibafu on 13/08/01.
 */
public class TweetAdapterWrap {
    private List<? extends TwitterResponse> statuses;
    private ViewConverter converter;
    private TweetAdapter adapter;
    private LayoutInflater inflater;
    private SharedPreferences preferences;
    private WeakReference<StatusManager> statusManager;

    public TweetAdapterWrap(Context context,
                            List<AuthUserRecord> userRecords,
                            List<UserExtras> userExtras,
                            List<? extends TwitterResponse> statuses,
                            Class<? extends TwitterResponse> contentClass) {
        this.statuses = statuses;
        adapter = new TweetAdapter(contentClass);
        inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        converter = ViewConverter.newInstance(
                context,
                userRecords,
                userExtras,
                PreferenceManager.getDefaultSharedPreferences(context),
                contentClass);
        preferences = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public TweetAdapter getAdapter() {
        return adapter;
    }

    public void notifyDataSetChanged() {
        adapter.notifyDataSetChanged();
    }

    public void setUserExtras(List<UserExtras> userExtras) {
        converter.setUserExtras(userExtras);
        notifyDataSetChanged();
    }

    public void setOnTouchProfileImageIconListener(OnTouchProfileImageIconListener listener) {
        converter.setOnTouchProfileImageIconListener(listener);
    }

    public interface OnTouchProfileImageIconListener {
        boolean onTouch(TwitterResponse element, View v, MotionEvent event);
    }

    public void setStatusManager(StatusManager statusManager) {
        this.statusManager = new WeakReference<>(statusManager);
    }

    public static class RecycleListener implements AbsListView.RecyclerListener {

        @Override
        public void onMovedToScrapHeap(View view) {
            if (view.getTag(R.string.key_viewholder) != null &&
                    view.getTag(R.string.key_viewholder) instanceof TweetViewHolder) {
                TweetViewHolder viewHolder = (TweetViewHolder) view.getTag(R.string.key_viewholder);
                viewHolder.recycleImageViews();
            }
        }
    }

    private class TweetAdapter extends BaseAdapter {
        private int clsType;
        private static final int CLS_STATUS = 0;
        private static final int CLS_DM = 1;
        private static final int CLS_UNKNOWN = 2;

        private static final int VT_CONVERTER = 0;
        private static final int VT_LOAD_MARKER = 1;
        private static final int VT_COUNT = 2;

        public TweetAdapter(Class<? extends TwitterResponse> clz) {
            if (Status.class.isAssignableFrom(clz)) {
                clsType = CLS_STATUS;
            } else if (DirectMessage.class.isAssignableFrom(clz)) {
                clsType = CLS_DM;
            } else {
                clsType = CLS_UNKNOWN;
            }
        }

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
            switch (clsType) {
                case CLS_STATUS:
                    return ((Status)statuses.get(position)).getId();
                case CLS_DM:
                    return ((DirectMessage)statuses.get(position)).getId();
                default:
                    return position;
            }
        }

        @Override
        public int getItemViewType(int position) {
            TwitterResponse item = getItem(position);
            if (item != null && item instanceof PreformedStatus && ((PreformedStatus) item).getBaseStatusClass() == LoadMarkerStatus.class) {
                return VT_LOAD_MARKER;
            }
            return VT_CONVERTER;
        }

        @Override
        public int getViewTypeCount() {
            return VT_COUNT;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TwitterResponse item = getItem(position);

            switch (getItemViewType(position)) {
                default:
                    if (convertView == null) {
                        convertView = inflater.inflate(preferences.getBoolean("pref_mode_singleline", false)?
                                R.layout.row_tweet_single : R.layout.row_tweet, null);
                    }

                    if (item != null) {
                        convertView = converter.convertView(convertView, item, ViewConverter.MODE_DEFAULT);
                    }
                    break;
                case VT_LOAD_MARKER:
                    if (convertView == null) {
                        convertView = inflater.inflate(R.layout.row_loading, null);
                    }

                    LoadMarkerStatus loadMarker = (LoadMarkerStatus) ((PreformedStatus) item).getBaseStatus();

                    ProgressBar progressBar = (ProgressBar) convertView.findViewById(R.id.pbLoading);
                    TextView textView = (TextView) convertView.findViewById(R.id.tvLoading);

                    if (statusManager != null && statusManager.get() != null && statusManager.get().isWorkingRestQuery(loadMarker.getTaskKey())) {
                        progressBar.setVisibility(View.VISIBLE);
                        textView.setText("loading");
                    } else {
                        progressBar.setVisibility(View.INVISIBLE);
                        textView.setText("more");
                    }
                    break;
            }

            return convertView;
        }
    }

    private static class TweetViewHolder {
        TextView tvName;
        TextView tvText;
        ImageView ivIcon, ivRetweeterIcon;
        ImageView ivProtected, ivFavorited;
        TextView tvTimestamp;
        LinearLayout llAttach;
        TextView tvReceived;
        LinearLayout flInclude;
        ImageView ivAccountColor;
        ImageView ivUserColor;

        private static final List<Field> textViews = new ArrayList<>();
        private static final List<Field> imageViews = new ArrayList<>();

        static {
            for (Field field : TweetViewHolder.class.getDeclaredFields()) {
                if (field.getName().startsWith("tv")) {
                    textViews.add(field);
                } else if (field.getName().startsWith("iv")) {
                    imageViews.add(field);
                }
            }
        }

        public TweetViewHolder(View v) {
            tvName = (TextView) v.findViewById(R.id.tweet_name);
            tvText = (TextView) v.findViewById(R.id.tweet_text);
            ivIcon = (ImageView)v.findViewById(R.id.tweet_icon);
            ivRetweeterIcon = (ImageView) v.findViewById(R.id.tweet_retweeter);
            ivProtected = (ImageView) v.findViewById(R.id.tweet_protected);
            ivFavorited = (ImageView) v.findViewById(R.id.tweet_faved);
            tvTimestamp = (TextView)v.findViewById(R.id.tweet_timestamp);
            llAttach = (LinearLayout) v.findViewById(R.id.tweet_attach);
            tvReceived = (TextView) v.findViewById(R.id.tweet_receive);
            flInclude = (LinearLayout) v.findViewById(R.id.tweet_include);
            ivAccountColor = (ImageView) v.findViewById(R.id.tweet_accountcolor);
            ivUserColor = (ImageView) v.findViewById(R.id.tweet_color);
        }

        public void setText(SharedPreferences pref, User user, int mode, String text) {
            if (pref.getBoolean("pref_mode_singleline", false) && ((ViewConverter.MODE_DETAIL | ViewConverter.MODE_PREVIEW) & mode) == 0) {
                SpannableStringBuilder sb = new SpannableStringBuilder();
                sb.append(user.getScreenName());
                sb.setSpan(new StyleSpan(Typeface.BOLD), 0, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.setSpan(new ForegroundColorSpan(Color.parseColor("#ff419b38")), 0, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.append(" ");
                sb.append(text.replace("\n", " "));
                tvText.setText(sb);
            } else {
                tvText.setText(text);
            }
        }

        public void hideTextViews() {
            for (Field field : textViews) {
                try {
                    ((View) field.get(this)).setVisibility(View.GONE);
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }

        public void recycleImageViews() {
            //ImageViewを全て解放
            for (Field field : imageViews) {
                try {
                    ((ImageView) field.get(this)).setImageDrawable(null);
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
            //Attachを全て解放
            if (llAttach != null) {
                for (int i = 0; i < llAttach.getChildCount(); ++i) {
                    View v = llAttach.getChildAt(i);
                    if (v != null && v instanceof ImageView) {
                        ((ImageView) v).setImageDrawable(null);
                    }
                }
            }
            //Includeを全て解放
            if (flInclude != null) {
                for (int i = 0; i < flInclude.getChildCount(); ++i) {
                    View v = flInclude.getChildAt(i);
                    if (v != null && v.getTag(R.string.key_viewholder) != null &&
                            v.getTag(R.string.key_viewholder) instanceof TweetViewHolder) {
                        ((TweetViewHolder) v.getTag(R.string.key_viewholder)).recycleImageViews();
                    }
                }
            }
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
        public static final int MODE_INCLUDE = 128;

        private static final String[] SEMI_CRY = {
                "ｼﾞｼﾞ…ｼﾞｼﾞｼﾞｼﾞ…………ﾐﾐﾐﾐﾐﾐﾝﾐﾝﾐﾝ!!!",
                "ミーーーン↑↑ｗｗｗｗｗミンミンミンミン↑↑ｗｗｗｗｗｗｗｗミーーーン↓↓ｗｗｗｗｗ",
                "ｱーーｼｬｯｼｬｯｼｬｯｼｬｯｼｬｯﾝﾎﾞｫｵｵーーｼｯ\nﾂｸﾂｸﾎﾞｫｵｵーｼｯ　ﾂｸﾂｸﾎﾞｫｵｵーｼｯ\nﾂｸﾂｸﾎﾞｫｵｵーｼｯ　ﾂｸﾂｸﾎﾞｫｵｵーｼｯ\nﾂｸﾂｸｳｨーﾖーｯ　ﾂｸｳｨーﾖーｯ　ﾂｸｳｨーﾖーｯ　ﾂｸｳｨーﾖーｯ\nｳｨｨｨｲｲｲｲｲｲｨｨｨｨｨｨーー……",
                "ｼﾞｰｰｰｰｰｰｰｰｰｰｰﾜｼﾜｼﾜｼﾜｼﾜｼﾜｼﾜｼﾜｼﾜｼﾜｼﾜｼﾜｼﾜｼ\nｼﾞｰｰｰｰｰｰｰｰｰｰｰﾜｼﾜｼﾜｼﾜｼﾜｼﾜｼﾜｼﾜｼﾜｼﾜｼﾜｼﾜｼﾜｼ\nｼｰｰｰｰｰｰｰｰｰｰｰ"
        };

        private Context context;
        private List<AuthUserRecord> userRecords;
        private List<UserExtras> userExtras;
        private SharedPreferences preferences;
        private TweetCommonDelegate delegate;

        private int bgDefaultResId;
        private int bgMentionResId;
        private int bgOwnResId;
        private OnTouchProfileImageIconListener onTouchProfileImageIconListener;

        @SuppressWarnings("TryWithIdenticalCatches")
        public static ViewConverter newInstance(
                Context context,
                List<AuthUserRecord> userRecords,
                List<UserExtras> userExtras,
                SharedPreferences sharedPreferences,
                Class<? extends TwitterResponse> cls) {
            try {
                if (PreformedStatus.class.isAssignableFrom(cls)) {
                    return new StatusViewConverter(context, userRecords, userExtras, sharedPreferences);
                }
                else if (DirectMessage.class.isAssignableFrom(cls)) {
                    return new MessageViewConverter(context, userRecords, userExtras, sharedPreferences);
                }
                else if (HistoryStatus.class.isAssignableFrom(cls)) {
                    return new HistoryViewConverter(context, userRecords, userExtras, sharedPreferences);
                }
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            } catch (InstantiationException e) {
                throw new RuntimeException(e);
            }
            throw new UnsupportedOperationException("対応されているクラスではありません.");
        }

        protected ViewConverter(Context context,
                                List<AuthUserRecord> userRecords,
                                List<UserExtras> userExtras,
                                SharedPreferences preferences,
                                TweetCommonDelegate delegate)
                throws IllegalAccessException, InstantiationException {
            this.context = context;
            this.userRecords = userRecords;
            this.userExtras = userExtras != null ? userExtras : new ArrayList<>();
            this.preferences = preferences;
            this.delegate = delegate;

            bgDefaultResId = AttrUtil.resolveAttribute(context.getTheme(), R.attr.tweetNormal);
            bgMentionResId = AttrUtil.resolveAttribute(context.getTheme(), R.attr.tweetMention);
            bgOwnResId = AttrUtil.resolveAttribute(context.getTheme(), R.attr.tweetOwn);
        }

        protected Context getContext() {
            return context;
        }

        protected List<AuthUserRecord> getUserRecords() {
            return userRecords;
        }

        protected List<UserExtras> getUserExtras() {
            return userExtras;
        }

        public void setUserExtras(List<UserExtras> userExtras) {
            this.userExtras = userExtras;
        }

        protected SharedPreferences getPreferences() {
            return preferences;
        }

        public void setOnTouchProfileImageIconListener(OnTouchProfileImageIconListener listener) {
            this.onTouchProfileImageIconListener = listener;
        }

        public View convertView(final View v, final TwitterResponse content, int mode) {
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
            viewHolder.tvName.setText("@" + u.getScreenName() + " / " +
                    (preferences.getBoolean("pref_remove_name_newline", false)? u.getName().replace('\n', ' ') : u.getName())
            );
            viewHolder.tvName.setTextSize(fontSize);

            viewHolder.tvText.setTypeface(FontAsset.getInstance(context).getFont());
            viewHolder.tvText.setTextSize(fontSize);
            String text = delegate.getText(content);
            if (preferences.getBoolean("j_grassleaf", false)) {
                text = text.replaceAll("(wwww|ｗｗ|。|\\.\\.\\.|…|・・・)", "wwwwwwwwwwwwwwwwwwwwwwwwwww")
                        .replaceAll("[？?]", "？wwwwwwwwwwwwwwwwwwww")
                        .replaceAll("[^＾][~〜]+", "＾〜〜〜〜wwwwwwwwwww");
                viewHolder.tvText.setTextColor(Color.parseColor("#0b5b12"));
            }
            if (preferences.getBoolean("j_cicada", false)) {
                text = SEMI_CRY[Math.abs(text.hashCode()) % SEMI_CRY.length];
            }
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
            viewHolder.setText(preferences, u, mode, text);

            String imageUrl = getPreferences().getBoolean("pref_narrow", false) ?
                    u.getProfileImageURLHttps() : u.getBiggerProfileImageURLHttps();
            if (viewHolder.ivIcon.getTag() == null || !viewHolder.ivIcon.getTag().equals(imageUrl)) {
                ImageLoaderTask.loadProfileIcon(context, viewHolder.ivIcon, imageUrl);
            }
            if (onTouchProfileImageIconListener != null) {
                viewHolder.ivIcon.setOnTouchListener((iv, event) -> onTouchProfileImageIconListener.onTouch(content, v, event));
            }

            viewHolder.tvTimestamp.setTypeface(FontAsset.getInstance(context).getFont());
            viewHolder.tvTimestamp.setTextSize(fontSize * 0.8f);

            if (delegate.getUser(content).isProtected()) {
                viewHolder.ivProtected.setVisibility(View.VISIBLE);
            }
            else {
                viewHolder.ivProtected.setVisibility(View.GONE);
            }

            int statusRelation = (userRecords != null) ?
                    delegate.getStatusRelation(userRecords, content) : 0;
            if (mode != MODE_PREVIEW) {
                switch (statusRelation) {
                    case TweetCommonDelegate.REL_MENTION:
                        v.setBackgroundResource(bgMentionResId);
                        break;
                    case TweetCommonDelegate.REL_OWN:
                        v.setBackgroundResource(bgOwnResId);
                        break;
                    default:
                        v.setBackgroundResource(bgDefaultResId);
                        break;
                }
            }
            viewHolder.tvTimestamp.setText(StringUtil.formatDate(delegate.getCreatedAt(content)) + " via " + delegate.getSource(content));

            if (mode == MODE_PREVIEW) {
                viewHolder.tvName.setTextColor(Color.BLACK);
                viewHolder.tvTimestamp.setTextColor(Color.BLACK);
                viewHolder.tvReceived.setTextColor(Color.BLACK);
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

        private int bgRetweetResId;
        private final LinearLayout.LayoutParams lpThumb;

        protected StatusViewConverter(Context context,
                                      List<AuthUserRecord> userRecords,
                                      List<UserExtras> userExtras,
                                      SharedPreferences preferences)
                throws IllegalAccessException, InstantiationException {
            super(context, userRecords, userExtras, preferences, TweetCommon.newInstance(PreformedStatus.class));
            bgRetweetResId = AttrUtil.resolveAttribute(context.getTheme(), R.attr.tweetRetweet);
            int attachPictureSize = context.getResources().getDimensionPixelSize(R.dimen.attach_picture_size);
            lpThumb = new LinearLayout.LayoutParams(attachPictureSize, attachPictureSize, 1);
        }

        @Override
        protected View convertViewAbs(View v, TweetViewHolder viewHolder, final TwitterResponse content, int mode) {
            final PreformedStatus st = (PreformedStatus) content;

            viewHolder.ivAccountColor.setBackgroundColor(st.getRepresentUser().AccountColor);
            {
                int color = Color.TRANSPARENT;
                for (UserExtras userExtra : getUserExtras()) {
                    if (userExtra.getId() == st.getSourceUser().getId()) {
                        color = userExtra.getColor();
                        break;
                    }
                }
                viewHolder.ivUserColor.setBackgroundColor(color);
            }

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
                hidden |= st.getOriginStatus().isCensoredThumbs();

                if (!hidden || getPreferences().getBoolean("pref_prev_mosaic", false)) {
                    List<LinkMedia> mediaList = st.getOriginStatus().getMediaLinkList();
                    int mlSize = mediaList.size();
                    if (mlSize > 0) {
                        viewHolder.llAttach.setVisibility(View.VISIBLE);
                        ImageView iv;
                        int i;
                        for (i = 0; i < mlSize; ++i) {
                            final LinkMedia media = mediaList.get(i);
                            iv = (ImageView) viewHolder.llAttach.findViewById(i);
                            if (iv == null) {
                                iv = new ImageView(getContext());
                                iv.setId(i);
                                viewHolder.llAttach.addView(iv, lpThumb);
                            }
                            else if (!getPreferences().getBoolean("pref_prev_mstrin", true) && media instanceof Meshi) {
                                iv.setVisibility(View.GONE);
                                continue;
                            }
                            else {
                                iv.setVisibility(View.VISIBLE);
                            }
                            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                            ImageLoaderTask.loadBitmap(getContext(),
                                    iv,
                                    media.getThumbURL(),
                                    BitmapCache.IMAGE_CACHE,
                                    hidden && getPreferences().getBoolean("pref_prev_mosaic", false));

                            if ((mode & MODE_DETAIL) == MODE_DETAIL && media.canPreview() || getPreferences().getBoolean("pref_extended_touch_event", false)) {
                                iv.setOnClickListener(v1 -> {
                                    Intent intent = new Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse(media.getBrowseURL()),
                                            getContext(),
                                            PreviewActivity.class);
                                    intent.putExtra(PreviewActivity.EXTRA_STATUS, st);
                                    getContext().startActivity(intent);
                                });
                            }
                        }
                        //使ってない分はしまっちゃおうね
                        int childCount = viewHolder.llAttach.getChildCount();
                        if (i < childCount) {
                            for (; i < childCount; ++i) {
                                viewHolder.llAttach.findViewById(i).setVisibility(View.GONE);
                            }
                        }
                    }
                    else {
                        viewHolder.llAttach.setVisibility(View.GONE);
                    }
                }
                else {
                    viewHolder.llAttach.setVisibility(View.GONE);
                }
            }
            else {
                viewHolder.llAttach.setVisibility(View.GONE);
            }

            if (mode == MODE_DEFAULT && st.isTooManyRepeatText() && getPreferences().getBoolean("pref_shorten_repeat_text", false)) {
                viewHolder.setText(getPreferences(), st.getSourceUser(), mode, st.getRepeatedSequence() + "\n...(repeat)...");
            }

            if (mode != MODE_PREVIEW) {
                if (st.isRetweet()) {
                    String timestamp = "RT by @" + st.getUser().getScreenName() + "\n" +
                            StringUtil.formatDate(st.getRetweetedStatus().getCreatedAt()) + " via " + st.getRetweetedStatus().getSource();
                    viewHolder.tvTimestamp.setText(timestamp);
                    viewHolder.tvName.setText("@" + st.getRetweetedStatus().getUser().getScreenName() + " / " + st.getRetweetedStatus().getUser().getName());
                    v.setBackgroundResource(bgRetweetResId);

                    if (st.getRetweetedStatus().getUser().isProtected()) {
                        viewHolder.ivProtected.setVisibility(View.VISIBLE);
                    }
                    else {
                        viewHolder.ivProtected.setVisibility(View.INVISIBLE);
                    }

                    viewHolder.ivRetweeterIcon.setVisibility(View.VISIBLE);
                    ImageLoaderTask.loadProfileIcon(getContext(),
                            viewHolder.ivRetweeterIcon,
                            st.getUser().getBiggerProfileImageURLHttps());
                }
                else {
                    viewHolder.ivRetweeterIcon.setVisibility(View.INVISIBLE);
                    viewHolder.ivRetweeterIcon.setImageDrawable(new ColorDrawable(Color.TRANSPARENT));
                }
            }

            if ((st.isRetweet() && st.getRetweetedStatus().getGeoLocation() != null) || st.getGeoLocation() != null) {
                GeoLocation geoLocation = st.isRetweet()? st.getRetweetedStatus().getGeoLocation() : st.getGeoLocation();
                viewHolder.tvTimestamp.setText(String.format("%s\nGeo: %f, %f",
                        viewHolder.tvTimestamp.getText(),
                        geoLocation.getLatitude(),
                        geoLocation.getLongitude()));
            }
            if (st.isCensoredThumbs()) {
                viewHolder.tvTimestamp.setText(viewHolder.tvTimestamp.getText() + "\n[Thumbnail Muted]");
            }

            if ((st.isRetweet() && st.getRetweetedStatus().isFavoritedSomeone()) || st.isFavoritedSomeone()) {
                viewHolder.ivFavorited.setVisibility(View.VISIBLE);
            }
            else {
                viewHolder.ivFavorited.setVisibility(View.GONE);
            }

            switch (mode) {
                default:
                    viewHolder.flInclude.setVisibility(View.GONE);
                    break;
                case MODE_DEFAULT:
                case MODE_DETAIL:
                    List<Long> quoteEntities = st.getQuoteEntities();
                    int qeSize = quoteEntities.size();
                    if (qeSize > 0) {
                        viewHolder.flInclude.removeAllViews();
                        viewHolder.flInclude.setVisibility(View.VISIBLE);
                        for (int i = 0; i < qeSize; i++) {
                            Long quoteId = quoteEntities.get(i);
                            if (StatusManager.getReceivedStatuses().get(quoteId) != null) {
                                View tv = View.inflate(getContext(),
                                        getPreferences().getBoolean("pref_mode_singleline", false)?
                                                R.layout.row_tweet_single : R.layout.row_tweet,
                                        null);
                                ViewConverter vc = ViewConverter.newInstance(getContext(), getUserRecords(), getUserExtras(), getPreferences(), PreformedStatus.class);
                                vc.convertView(tv, StatusManager.getReceivedStatuses().get(quoteId), mode | MODE_INCLUDE);
                                viewHolder.flInclude.addView(tv);
                            }
                        }
                    }
                    else {
                        viewHolder.flInclude.setVisibility(View.GONE);
                    }
                    break;
            }

            if (getPreferences().getBoolean("j_fullmute", false)) {
                viewHolder.hideTextViews();
            }

            return v;
        }
    }

    private static class MessageViewConverter extends ViewConverter {

        protected MessageViewConverter(Context context,
                                       List<AuthUserRecord> userRecords,
                                       List<UserExtras> userExtras,
                                       SharedPreferences preferences)
                throws IllegalAccessException, InstantiationException {
            super(context, userRecords, userExtras, preferences, TweetCommon.newInstance(DirectMessage.class));
        }

        @Override
        protected View convertViewAbs(View v, TweetViewHolder viewHolder, TwitterResponse content, int mode) {
            final DirectMessage message = (DirectMessage) content;
            viewHolder.ivAccountColor.setBackgroundColor(Color.TRANSPARENT);
            for (AuthUserRecord authUserRecord : getUserRecords()) {
                if (authUserRecord.NumericId == message.getRecipientId()) {
                    viewHolder.ivAccountColor.setBackgroundColor(authUserRecord.AccountColor);
                    break;
                } else if (authUserRecord.NumericId == message.getSenderId()) {
                    viewHolder.ivAccountColor.setBackgroundColor(authUserRecord.AccountColor);
                }
            }
            viewHolder.tvTimestamp.setText(String.format("%s to @%s / %s",
                    viewHolder.tvTimestamp.getText(),
                    message.getRecipientScreenName(), message.getRecipient().getName()));
            return v;
        }
    }

    private static class HistoryViewConverter extends ViewConverter {

        private ViewConverter includeViewConverter;

        protected HistoryViewConverter(Context context,
                                       List<AuthUserRecord> userRecords,
                                       List<UserExtras> userExtras,
                                       SharedPreferences preferences)
                throws IllegalAccessException, InstantiationException {
            super(context, userRecords, userExtras, preferences, TweetCommon.newInstance(HistoryStatus.class));
            includeViewConverter = ViewConverter.newInstance(context, userRecords, userExtras, preferences, PreformedStatus.class);
        }

        private String kindToString(int kind) {
            switch (kind) {
                case HistoryStatus.KIND_FAVED:
                    return "お気に入り登録";
                case HistoryStatus.KIND_RETWEETED:
                    return "リツイート";
                default:
                    return "反応";
            }
        }

        @Override
        protected View convertViewAbs(View v, TweetViewHolder viewHolder, TwitterResponse content, int mode) {
            final HistoryStatus historyStatus = (HistoryStatus) content;
            viewHolder.tvText.setVisibility(View.GONE);
            viewHolder.tvName.setText(String.format("@%sさんが%s", historyStatus.getUser().getScreenName(), kindToString(historyStatus.getKind())));
            viewHolder.tvTimestamp.setText(StringUtil.formatDate(historyStatus.getCreatedAt()));

            viewHolder.flInclude.setVisibility(View.VISIBLE);
            View include;
            if (viewHolder.flInclude.getChildCount() > 0) {
                include = viewHolder.flInclude.getChildAt(0);
            } else {
                include = View.inflate(getContext(), R.layout.row_tweet, null);
                viewHolder.flInclude.addView(include);
            }
            includeViewConverter.convertView(include, historyStatus.getStatus(), mode | MODE_INCLUDE);
            return v;
        }
    }
}
