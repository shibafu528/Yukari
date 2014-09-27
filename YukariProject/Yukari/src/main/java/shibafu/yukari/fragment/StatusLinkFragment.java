package shibafu.yukari.fragment;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.ListFragment;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import shibafu.yukari.R;
import shibafu.yukari.activity.MainActivity;
import shibafu.yukari.activity.PreviewActivity;
import shibafu.yukari.activity.ProfileActivity;
import shibafu.yukari.activity.StatusActivity;
import shibafu.yukari.activity.TraceActivity;
import shibafu.yukari.activity.TweetActivity;
import shibafu.yukari.fragment.tabcontent.DefaultTweetListFragment;
import shibafu.yukari.fragment.tabcontent.TweetListFragment;
import shibafu.yukari.media.LinkMedia;
import shibafu.yukari.twitter.AuthUserRecord;
import shibafu.yukari.twitter.TwitterUtil;
import shibafu.yukari.twitter.statusimpl.PreformedStatus;
import twitter4j.GeoLocation;
import twitter4j.HashtagEntity;
import twitter4j.Status;
import twitter4j.URLEntity;
import twitter4j.User;
import twitter4j.UserMentionEntity;

/**
 * Created by Shibafu on 13/08/02.
 */
public class StatusLinkFragment extends ListFragment{

    private static final int TYPE_URL       = 0x01;
    private static final int TYPE_URL_MEDIA = 0x20;

    private static final int TYPE_HASH  = 0x02;
    private static final int TYPE_TRACE = 0x04;
    private static final int TYPE_GEO = 0x08;

    private static final int TYPE_USER         = 0x10;
    private static final int TYPE_USER_REPLY   = TYPE_USER | 0x01;
    private static final int TYPE_USER_DM      = TYPE_USER | 0x02;
    private static final int TYPE_USER_PROFILE = TYPE_USER | 0x04;

    private PreformedStatus status = null;
    private AuthUserRecord user = null;
    private List<LinkRow> list;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        Bundle b = getArguments();
        status = (PreformedStatus) b.getSerializable(StatusActivity.EXTRA_STATUS);
        Status rt_status = null;
        boolean rt = false;
        if (status.isRetweet()) {
            rt = true;
            rt_status = status;
            status = status.getRetweetedStatus();
        }
        user = (AuthUserRecord) b.getSerializable(StatusActivity.EXTRA_USER);

        PackageManager pm = getActivity().getPackageManager();

        //リスト要素を作成する
        list = new ArrayList<LinkRow>() {
            @Override
            public boolean add(LinkRow object) {
                return !list.contains(object) && super.add(object);
            }
        };
        for (LinkMedia lm : status.getMediaLinkList()) {
            list.add(new LinkRow(lm.getBrowseURL(), (TYPE_URL | (lm.canPreview()? TYPE_URL_MEDIA : 0)), 0, null, false));
        }
        for (URLEntity u : status.getURLEntities()) {
            LinkRow row = new LinkRow(u.getExpandedURL(), TYPE_URL, 0, null, false);
            Uri uri = Uri.parse(u.getExpandedURL());
            Intent intent = new Intent(Plugin.ACTION_LINK_ACCEL, uri);
            for (ResolveInfo resolveInfo : pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)) {
                row.plugins.add(new Plugin(pm, resolveInfo, uri));
            }
            list.add(row);
        }
        for (HashtagEntity h : status.getHashtagEntities()) {
            list.add(new LinkRow("#" + h.getText(), TYPE_HASH, 0, null, false));
        }
        if (status.getGeoLocation() != null) {
            GeoLocation geoLocation = status.getGeoLocation();
            String query = String.format("geo:0,0?q=%f,%f", geoLocation.getLatitude(), geoLocation.getLongitude());
            list.add(new LinkRow(query, TYPE_GEO, 0, null, false));
        }
        if (status.getInReplyToStatusId() > -1) {
            list.add(new LinkRow("会話をたどる", TYPE_TRACE, 0, null, false));
        }
        if (rt) {
            User u = rt_status.getUser();
            list.add(new LinkRow("返信", TYPE_USER_REPLY, u.getId(), u.getScreenName(), true));
            list.add(new LinkRow("DMを送る", TYPE_USER_DM, u.getId(), u.getScreenName(), false));
            list.add(new LinkRow("ユーザー情報を見る", TYPE_USER_PROFILE, u.getId(), u.getScreenName(), false));
        }
        {
            User u = status.getUser();
            boolean skip = false;
            for (LinkRow lr : list) {
                if (lr.targetUser == u.getId()) {
                    skip = true;
                    break;
                }
            }
            if (!skip) {
                list.add(new LinkRow("返信", TYPE_USER_REPLY, u.getId(), u.getScreenName(), true));
                list.add(new LinkRow("DMを送る", TYPE_USER_DM, u.getId(), u.getScreenName(), false));
                list.add(new LinkRow("ユーザー情報を見る", TYPE_USER_PROFILE, u.getId(), u.getScreenName(), false));
            }
        }
        for (UserMentionEntity u : status.getUserMentionEntities()) {
            boolean skip = false;
            for (LinkRow lr : list) {
                if (lr.targetUser == u.getId()) {
                    skip = true;
                    break;
                }
            }
            if (!skip) {
                list.add(new LinkRow("返信", TYPE_USER_REPLY, u.getId(), u.getScreenName(), true));
                list.add(new LinkRow("DMを送る", TYPE_USER_DM, u.getId(), u.getScreenName(), false));
                list.add(new LinkRow("ユーザー情報を見る", TYPE_USER_PROFILE, u.getId(), u.getScreenName(), false));
            }
        }

        setListAdapter(new LinkAdapter(getActivity(), list));
        getListView().setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                final LinkRow lr = list.get(position);
                if ((lr.type & TYPE_USER) == TYPE_USER) {
                    switch (lr.type) {
                        case TYPE_USER_REPLY:
                        case TYPE_USER_DM: {
                            Intent intent = new Intent(getActivity(), TweetActivity.class);
                            intent.putExtra(TweetActivity.EXTRA_USER, user);
                            if (lr.type == TYPE_USER_REPLY) {
                                intent.putExtra(TweetActivity.EXTRA_TEXT, "@" + lr.targetUserSN + " ");
                                intent.putExtra(TweetActivity.EXTRA_MODE, TweetActivity.MODE_REPLY);
                            } else {
                                intent.putExtra(TweetActivity.EXTRA_MODE, TweetActivity.MODE_DM);
                                intent.putExtra(TweetActivity.EXTRA_IN_REPLY_TO, lr.targetUser);
                                intent.putExtra(TweetActivity.EXTRA_DM_TARGET_SN, lr.targetUserSN);
                            }
                            startActivity(intent);
                            break;
                        }
                        case TYPE_USER_PROFILE: {
                            Intent intent = new Intent(getActivity(), ProfileActivity.class);
                            intent.putExtra(ProfileActivity.EXTRA_USER, user);
                            intent.putExtra(ProfileActivity.EXTRA_TARGET, lr.targetUser);
                            startActivity(intent);
                            break;
                        }
                    }
                } else {
                    class BrowserExecutor {
                        void executeIntent(Uri uri) {
                            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                        }
                    }
                    switch (lr.type) {
                        case TYPE_URL: {
                                final Uri uri = Uri.parse(lr.text);
                                final BrowserExecutor executor = new BrowserExecutor();
                                if (lr.type == TYPE_URL && uri.getHost().contains("www.google")) {
                                    String lastPathSegment = uri.getLastPathSegment();
                                    if (lastPathSegment != null && lastPathSegment.equals("search")) {
                                        String query = uri.getQueryParameter("q");
                                        AlertDialog ad = new AlertDialog.Builder(getActivity())
                                                .setTitle("検索URL")
                                                .setMessage("検索キーワードは「" + query + "」です。\nブラウザで開きますか？")
                                                .setPositiveButton("続行", new DialogInterface.OnClickListener() {
                                                    @Override
                                                    public void onClick(DialogInterface dialog, int which) {
                                                        dialog.dismiss();
                                                        executor.executeIntent(uri);
                                                    }
                                                })
                                                .setNegativeButton("キャンセル", new DialogInterface.OnClickListener() {
                                                    @Override
                                                    public void onClick(DialogInterface dialog, int which) {
                                                        dialog.dismiss();
                                                    }
                                                })
                                                .create();
                                        ad.show();
                                    } else {
                                        executor.executeIntent(uri);
                                    }
                                } else {
                                    executor.executeIntent(uri);
                                }
                            break;
                        }
                        case TYPE_GEO: {
                            new BrowserExecutor().executeIntent(Uri.parse(lr.text));
                            break;
                        }
                        case (TYPE_URL | TYPE_URL_MEDIA): {
                            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(lr.text), getActivity(), PreviewActivity.class);
                            intent.putExtra(PreviewActivity.EXTRA_STATUS, status);
                            startActivity(intent);
                            break;
                        }
                        case TYPE_HASH: {
                            AlertDialog ad = new AlertDialog.Builder(getActivity())
                                    .setTitle(lr.text)
                                    .setPositiveButton("つぶやく", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            dialog.dismiss();

                                            Intent intent = new Intent(getActivity(), TweetActivity.class);
                                            intent.putExtra(TweetActivity.EXTRA_USER, user);
                                            intent.putExtra(TweetActivity.EXTRA_TEXT, " " + lr.text);
                                            startActivity(intent);
                                        }
                                    })
                                    .setNegativeButton("検索する", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            dialog.dismiss();

                                            Intent intent = new Intent(getActivity(), MainActivity.class);
                                            intent.putExtra(MainActivity.EXTRA_SEARCH_WORD, lr.text);
                                            startActivity(intent);
                                        }
                                    })
                                    .setNeutralButton("キャンセル", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            dialog.dismiss();
                                        }
                                    })
                                    .create();
                            ad.show();
                            break;
                        }
                        case TYPE_TRACE: {
                            Intent intent = new Intent(getActivity(), TraceActivity.class);
                            intent.putExtra(TweetListFragment.EXTRA_USER, user);
                            intent.putExtra(TweetListFragment.EXTRA_TITLE, "Trace");
                            intent.putExtra(DefaultTweetListFragment.EXTRA_TRACE_START, status);
                            startActivity(intent);
                            break;
                        }
                    }
                }
            }
        });
        getListView().setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                final LinkRow lr = list.get(position);
                if ((lr.type & TYPE_USER) != TYPE_USER) {
                    switch (lr.type) {
                        case (TYPE_URL | TYPE_URL_MEDIA):
                        {
                            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(lr.text));
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                            return true;
                        }
                    }
                }
                return false;
            }
        });

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getActivity());
        getListView().setStackFromBottom(sp.getBoolean("pref_bottom_stack", false));
    }

    private class Plugin {
        public static final String ACTION_LINK_ACCEL = "shibafu.yukari.ACTION_LINK_ACCEL";
        private ResolveInfo resolveInfo;
        private Drawable icon;
        private CharSequence label;
        private boolean canReceiveName;
        private Uri uri;

        Plugin(PackageManager pm, ResolveInfo ri, Uri uri) {
            resolveInfo = ri;
            icon = ri.loadIcon(pm);
            label = ri.loadLabel(pm);
            try {
                ActivityInfo info = pm.getActivityInfo(new ComponentName(resolveInfo.activityInfo.packageName, resolveInfo.activityInfo.name), PackageManager.GET_META_DATA);
                canReceiveName = info.metaData.getBoolean("can_receive_name", false);
            } catch (NullPointerException | PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
            this.uri = uri;
        }

        public void executeIntent(boolean grantName) {
            Intent intent = new Intent(ACTION_LINK_ACCEL, uri);
            intent.setPackage(resolveInfo.activityInfo.packageName);
            intent.setClassName(resolveInfo.activityInfo.packageName, resolveInfo.activityInfo.name);
            intent.putExtra("grant_name", grantName);
            intent.putExtra("user_name", user.Name);
            intent.putExtra("user_screen_name", user.ScreenName);
            intent.putExtra("user_id", user.NumericId);
            intent.putExtra("user_profile_image_url", user.ProfileImageUrl);
            intent.putExtra("status_url", TwitterUtil.getTweetURL(status));
            startActivity(intent);
        }
    }

    private class LinkRow {
        public String text;
        public int type;
        public long targetUser;
        public String targetUserSN;
        public boolean showTargetUser;
        public List<Plugin> plugins = new ArrayList<>();

        private LinkRow(String text, int type, long targetUser, String targetUserSN, boolean showTargetUser) {
            this.text = text;
            this.type = type;
            this.targetUser = targetUser;
            this.targetUserSN = targetUserSN;
            this.showTargetUser = showTargetUser;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            LinkRow linkRow = (LinkRow) o;

            if (showTargetUser != linkRow.showTargetUser) return false;
            if (targetUser != linkRow.targetUser) return false;
            if (type != linkRow.type) return false;
            if (targetUserSN != null ? !targetUserSN.equals(linkRow.targetUserSN) : linkRow.targetUserSN != null)
                return false;
            if (text != null ? !text.equals(linkRow.text) : linkRow.text != null) return false;

            return true;
        }

        @Override
        public int hashCode() {
            int result = text != null ? text.hashCode() : 0;
            result = 31 * result + type;
            result = 31 * result + (int) (targetUser ^ (targetUser >>> 32));
            result = 31 * result + (targetUserSN != null ? targetUserSN.hashCode() : 0);
            result = 31 * result + (showTargetUser ? 1 : 0);
            return result;
        }
    }

    private class LinkAdapter extends ArrayAdapter<LinkRow> {

        private LayoutInflater inflater;

        public LinkAdapter(Context context, List<LinkRow> objects) {
            super(context, 0, objects);
            inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;

            if (v == null) {
                v = inflater.inflate(R.layout.row_statuslink, parent, false);
            }

            LinkRow d = getItem(position);
            if (d != null) {
                TextView tvTarget = (TextView) v.findViewById(R.id.statuslink_category);
                if (d.showTargetUser) {
                    tvTarget.setVisibility(View.VISIBLE);
                    tvTarget.setText("@" + d.targetUserSN);
                }
                else {
                    tvTarget.setVisibility(View.GONE);
                }

                TextView tvContent = (TextView) v.findViewById(R.id.statuslink_content);
                tvContent.setText(d.text);

                LinearLayout ll = (LinearLayout) v.findViewById(R.id.linearLayout);
                ll.removeAllViews();
                final float density = getResources().getDisplayMetrics().density;
                final int iconSize = (int) (density * 24);
                if (d.plugins.size() > 0) {
                    ll.setPadding(0, 0, (int) (density * 4), 0);
                }
                for (final Plugin plugin : d.plugins) {
                    ImageButton ib = new ImageButton(getContext());
                    ib.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                    ib.setImageBitmap(Bitmap.createScaledBitmap(((BitmapDrawable) plugin.icon).getBitmap(), iconSize, iconSize, true));
                    ib.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            plugin.executeIntent(plugin.canReceiveName);
                        }
                    });
                    ib.setOnLongClickListener(new View.OnLongClickListener() {
                        @Override
                        public boolean onLongClick(View v) {
                            Toast toast = Toast.makeText(getContext(), plugin.label, Toast.LENGTH_SHORT);
                            toast.setGravity(Gravity.TOP, 0, 0);
                            toast.show();
                            return true;
                        }
                    });
                    ll.addView(ib, FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
                }
            }

            return v;
        }
    }
}
