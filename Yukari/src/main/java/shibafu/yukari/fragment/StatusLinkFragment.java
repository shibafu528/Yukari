package shibafu.yukari.fragment;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.TextView;

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
import shibafu.yukari.twitter.PreformedStatus;
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

        //リスト要素を作成する
        list = new ArrayList<LinkRow>();
        for (LinkMedia lm : status.getMediaLinkList()) {
            list.add(new LinkRow(lm.getBrowseURL(), (TYPE_URL | (lm.canPreview()? TYPE_URL_MEDIA : 0)), 0, null, false));
        }
        for (URLEntity u : status.getURLEntities()) {
            list.add(new LinkRow(u.getExpandedURL(), TYPE_URL, 0, null, false));
        }
        for (HashtagEntity h : status.getHashtagEntities()) {
            list.add(new LinkRow("#" + h.getText(), TYPE_HASH, 0, null, false));
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
                        case TYPE_USER_DM:
                        {
                            Intent intent = new Intent(getActivity(), TweetActivity.class);
                            intent.putExtra(TweetActivity.EXTRA_USER, user);
                            if (lr.type == TYPE_USER_REPLY) {
                                intent.putExtra(TweetActivity.EXTRA_TEXT, "@" + lr.targetUserSN + " ");
                                intent.putExtra(TweetActivity.EXTRA_MODE, TweetActivity.MODE_REPLY);
                            }
                            else {
                                intent.putExtra(TweetActivity.EXTRA_MODE, TweetActivity.MODE_DM);
                                intent.putExtra(TweetActivity.EXTRA_IN_REPLY_TO, lr.targetUser);
                                intent.putExtra(TweetActivity.EXTRA_DM_TARGET_SN, lr.targetUserSN);
                            }
                            startActivity(intent);
                            break;
                        }
                        case TYPE_USER_PROFILE:
                        {
                            Intent intent = new Intent(getActivity(), ProfileActivity.class);
                            intent.putExtra(ProfileActivity.EXTRA_USER, user);
                            intent.putExtra(ProfileActivity.EXTRA_TARGET, lr.targetUser);
                            startActivity(intent);
                            break;
                        }
                    }
                }
                else {
                    switch (lr.type) {
                        case TYPE_URL:
                        {
                            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(lr.text));
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                            break;
                        }
                        case (TYPE_URL | TYPE_URL_MEDIA):
                        {
                            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(lr.text), getActivity(), PreviewActivity.class);
                            intent.putExtra(PreviewActivity.EXTRA_STATUS, status);
                            startActivity(intent);
                            break;
                        }
                        case TYPE_HASH:
                        {
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
                        case TYPE_TRACE:
                        {
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
    }

    private class LinkRow {
        public String text;
        public int type;
        public long targetUser;
        public String targetUserSN;
        public boolean showTargetUser;

        private LinkRow(String text, int type, long targetUser, String targetUserSN, boolean showTargetUser) {
            this.text = text;
            this.type = type;
            this.targetUser = targetUser;
            this.targetUserSN = targetUserSN;
            this.showTargetUser = showTargetUser;
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
            }

            return v;
        }
    }
}
