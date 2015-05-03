package shibafu.yukari.fragment;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.DialogFragment;
import android.text.ClipboardManager;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import shibafu.yukari.R;
import shibafu.yukari.activity.MuteActivity;
import shibafu.yukari.activity.StatusActivity;
import shibafu.yukari.database.Bookmark;
import shibafu.yukari.database.MuteConfig;
import shibafu.yukari.fragment.base.ListTwitterFragment;
import shibafu.yukari.media.LinkMedia;
import shibafu.yukari.twitter.AuthUserRecord;
import shibafu.yukari.twitter.TwitterUtil;
import shibafu.yukari.twitter.statusimpl.PreformedStatus;
import twitter4j.HashtagEntity;
import twitter4j.URLEntity;
import twitter4j.UserMentionEntity;

/**
 * Created by Shibafu on 13/08/02.
 */
@SuppressWarnings("deprecation")
public class StatusActionFragment extends ListTwitterFragment implements AdapterView.OnItemClickListener {
    private static final String[] ITEMS = {
            "ブラウザで開く",
            "パーマリンクをコピー",
            "ブックマークに追加",
            "リストへ追加/削除",
            "ミュートする",
            "ツイート削除"
    };

    private boolean enableDelete = true;
    private List<ResolveInfo> plugins;
    private List<String> pluginNames = new ArrayList<>();

    private PreformedStatus status = null;
    private AuthUserRecord user = null;

    private AlertDialog currentDialog = null;

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle b = getArguments();
        status = (PreformedStatus) b.getSerializable(StatusActivity.EXTRA_STATUS);
        user = (AuthUserRecord) b.getSerializable(StatusActivity.EXTRA_USER);

        if ((!status.isRetweet() && !status.getUser().isProtected()) ||
                (status.isRetweet() && !status.getRetweetedStatus().getUser().isProtected())) {
            PackageManager pm = getActivity().getPackageManager();
            Intent query = new Intent("jp.r246.twicca.ACTION_SHOW_TWEET");
            query.addCategory(Intent.CATEGORY_DEFAULT);
            plugins = pm.queryIntentActivities(query, PackageManager.MATCH_DEFAULT_ONLY);
            Collections.sort(plugins, new ResolveInfo.DisplayNameComparator(pm));
            pluginNames.clear();
            for (ResolveInfo ri : plugins) {
                pluginNames.add(ri.activityInfo.loadLabel(pm).toString());
            }
        }

        switch (PreferenceManager.getDefaultSharedPreferences(getActivity()).getString("pref_theme", "light")) {
            case "light":
                view.setBackgroundResource(R.drawable.dialog_full_holo_light);
                break;
            case "dark":
                view.setBackgroundResource(R.drawable.dialog_full_holo_dark);
                break;
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        ArrayList<String> menu = new ArrayList<>();
        menu.addAll(Arrays.asList(ITEMS));
        menu.addAll(pluginNames);

        if (!(status instanceof Bookmark) && (user == null || status.getUser().getId() != user.NumericId)) {
            menu.remove(ITEMS.length - 1);
            enableDelete = false;
        }

        setListAdapter(new ArrayAdapter<>(getActivity(), android.R.layout.simple_list_item_1, menu));
        getListView().setOnItemClickListener(this);

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getActivity());
        getListView().setStackFromBottom(sp.getBoolean("pref_bottom_stack", false));
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        switch (position) {
            case 0:
                Intent target = new Intent(Intent.ACTION_VIEW, Uri.parse(TwitterUtil.getTweetURL(status)));
                target.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(Intent.createChooser(target, null));
                break;
            case 1:
                ClipboardManager cb = (ClipboardManager) getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
                cb.setText(TwitterUtil.getTweetURL(status));
                Toast.makeText(getActivity(), "リンクをコピーしました", Toast.LENGTH_SHORT).show();
                break;
            case 2:
                getTwitterService().getDatabase().updateRecord(new Bookmark(status));
                Toast.makeText(getActivity(), "ブックマークしました", Toast.LENGTH_SHORT).show();
                break;
            case 3:
            {
                ListRegisterDialogFragment fragment = ListRegisterDialogFragment.newInstance(
                        status.isRetweet() ? status.getRetweetedStatus().getUser() : status.getUser());
                fragment.setTargetFragment(this, 0);
                fragment.show(getChildFragmentManager(), "register");
                break;
            }
            case 4:
                MuteMenuDialogFragment.newInstance(status, this).show(getChildFragmentManager(), "mute");
                break;
            default:
            {
                if (position == ITEMS.length-1 && enableDelete) {
                    AlertDialog ad = new AlertDialog.Builder(getActivity())
                            .setTitle("確認")
                            .setMessage("ツイートを削除しますか？")
                            .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                    currentDialog = null;

                                    AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
                                        @Override
                                        protected Void doInBackground(Void... params) {
                                            if (status instanceof Bookmark) {
                                                getTwitterService().getDatabase().deleteRecord((Bookmark) status);
                                            } else {
                                                getTwitterService().destroyStatus(user, status.getId());
                                            }
                                            return null;
                                        }
                                    };
                                    task.execute();
                                    getActivity().finish();
                                }
                            })
                            .setNegativeButton("キャンセル", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                    currentDialog = null;
                                }
                            })
                            .setOnCancelListener(new DialogInterface.OnCancelListener() {
                                @Override
                                public void onCancel(DialogInterface dialog) {
                                    dialog.dismiss();
                                    currentDialog = null;
                                }
                            })
                            .create();
                    ad.show();
                    currentDialog = ad;
                }
                else {
                    Intent intent;
                    if (user != null && status.getUser().getId() == user.NumericId) {
                        intent = createPluginIntent(position - ITEMS.length);
                    }
                    else {
                        intent = createPluginIntent(position - ITEMS.length + 1);
                    }
                    if (intent != null) {
                        try {
                            startActivity(intent);
                        } catch (ActivityNotFoundException e) {
                            Toast.makeText(getActivity().getApplicationContext(), "プラグインの起動に失敗しました\nアプリが削除されましたか？", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
                break;
            }
        }
    }

    private Intent createPluginIntent(int id) {
        ResolveInfo ri = plugins.get(id);
        if (ri != null) {
            Intent intent = new Intent("jp.r246.twicca.ACTION_SHOW_TWEET");
            intent.addCategory(Intent.CATEGORY_DEFAULT);
            intent.setPackage(ri.activityInfo.packageName);
            intent.setClassName(ri.activityInfo.packageName, ri.activityInfo.name);

            intent.putExtra(Intent.EXTRA_TEXT, status.getText());
            intent.putExtra("id", String.valueOf(status.getId()));
            intent.putExtra("created_at", String.valueOf(status.getCreatedAt().getTime()));

            Pattern VIA_PATTERN = Pattern.compile("<a .*>(.+)</a>");
            Matcher matcher = VIA_PATTERN.matcher(status.getSource());
            String via;
            if (matcher.find()) {
                via = matcher.group(1);
            }
            else {
                via = status.getSource();
            }
            intent.putExtra("source", via);
            if (status.getInReplyToStatusId() > -1) {
                intent.putExtra("in_reply_to_status_id", status.getInReplyToStatusId());
            }
            intent.putExtra("user_screen_name", status.getUser().getScreenName());
            intent.putExtra("user_name", status.getUser().getName());
            intent.putExtra("user_id", String.valueOf(status.getUser().getId()));
            intent.putExtra("user_profile_image_url", status.getUser().getProfileImageURL());
            intent.putExtra("user_profile_image_url_mini", status.getUser().getMiniProfileImageURL());
            intent.putExtra("user_profile_image_url_normal", status.getUser().getOriginalProfileImageURL());
            intent.putExtra("user_profile_image_url_bigger", status.getUser().getBiggerProfileImageURL());

            return intent;
        }
        return null;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (currentDialog != null) {
            currentDialog.show();
        }
    }

    public void onSelectedMuteOption(QuickMuteOption muteOption) {
        startActivity(muteOption.toIntent(getActivity()));
    }

    @Override
    public void onServiceConnected() {}

    @Override
    public void onServiceDisconnected() {}

    public static class MuteMenuDialogFragment extends DialogFragment {

        public static MuteMenuDialogFragment newInstance(PreformedStatus status, StatusActionFragment target) {
            MuteMenuDialogFragment fragment = new MuteMenuDialogFragment();
            Bundle args = new Bundle();
            args.putSerializable("status", status);
            fragment.setArguments(args);
            fragment.setTargetFragment(target, 0);
            return fragment;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            Bundle args = getArguments();
            PreformedStatus status = (PreformedStatus) args.getSerializable("status");
            final QuickMuteOption[] muteOptions = QuickMuteOption.fromStatus(status);
            String[] items = new String[muteOptions.length];
            for (int i = 0; i < items.length; ++i) {
                items[i] = muteOptions[i].toString();
            }

            AlertDialog dialog = new AlertDialog.Builder(getActivity())
                    .setTitle("ミュート")
                    .setItems(items, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dismiss();
                            StatusActionFragment fragment = (StatusActionFragment) getTargetFragment();
                            if (fragment != null) {
                                fragment.onSelectedMuteOption(muteOptions[which]);
                            }
                        }
                    })
                    .setNegativeButton("キャンセル", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                        }
                    })
                    .create();
            return dialog;
        }
    }

    private static class QuickMuteOption {
        public static final int TYPE_TEXT = 0;
        public static final int TYPE_USER_NAME = 1;
        public static final int TYPE_SCREEN_NAME = 2;
        public static final int TYPE_USER_ID = 3;
        public static final int TYPE_VIA = 4;
        public static final int TYPE_HASHTAG = 5;
        public static final int TYPE_MENTION = 6;
        public static final int TYPE_URL = 7;

        private int type;
        private String value;

        private QuickMuteOption(int type, String value) {
            this.type = type;
            this.value = value;
        }

        @Override
        public String toString() {
            switch (type) {
                case TYPE_TEXT:
                    return "本文";
                case TYPE_USER_NAME:
                    return "ユーザー名(" + value + ")";
                case TYPE_SCREEN_NAME:
                    return "スクリーンネーム(@" + value + ")";
                case TYPE_USER_ID:
                    return "ユーザーID(" + value + ")";
                case TYPE_VIA:
                    return "クライアント名(" + value + ")";
                case TYPE_HASHTAG:
                    return "#" + value;
                case TYPE_MENTION:
                    return "@" + value;
                default:
                    return value;
            }
        }

        public Intent toIntent(Context context) {
            String query = value;
            int which = MuteConfig.SCOPE_TEXT;
            int match = MuteConfig.MATCH_EXACT;
            switch (type) {
                case TYPE_TEXT:
                    which = MuteConfig.SCOPE_TEXT;
                    break;
                case TYPE_USER_NAME:
                    which = MuteConfig.SCOPE_USER_NAME;
                    break;
                case TYPE_SCREEN_NAME:
                    which = MuteConfig.SCOPE_USER_SN;
                    break;
                case TYPE_USER_ID:
                    which = MuteConfig.SCOPE_USER_ID;
                    break;
                case TYPE_VIA:
                    which = MuteConfig.SCOPE_VIA;
                    break;
                case TYPE_HASHTAG:
                    query = "[#＃]" + value;
                    match = MuteConfig.MATCH_REGEX;
                    break;
                case TYPE_MENTION:
                    query = "@" + value;
                    match = MuteConfig.MATCH_PARTIAL;
                    break;
                case TYPE_URL:
                    match = MuteConfig.MATCH_PARTIAL;
                    break;
            }
            return new Intent(context, MuteActivity.class)
                    .putExtra(MuteActivity.EXTRA_QUERY, query)
                    .putExtra(MuteActivity.EXTRA_SCOPE, which)
                    .putExtra(MuteActivity.EXTRA_MATCH, match);
        }

        public static QuickMuteOption[] fromStatus(PreformedStatus status) {
            List<QuickMuteOption> options = new ArrayList<>();
            options.add(new QuickMuteOption(TYPE_TEXT, status.getText()));
            options.add(new QuickMuteOption(TYPE_USER_NAME, status.getSourceUser().getName()));
            options.add(new QuickMuteOption(TYPE_SCREEN_NAME, status.getSourceUser().getScreenName()));
            options.add(new QuickMuteOption(TYPE_USER_ID, String.valueOf(status.getSourceUser().getId())));
            options.add(new QuickMuteOption(TYPE_VIA, status.getSource()));
            for (HashtagEntity hashtagEntity : status.getHashtagEntities()) {
                options.add(new QuickMuteOption(TYPE_HASHTAG, hashtagEntity.getText()));
            }
            for (UserMentionEntity userMentionEntity : status.getUserMentionEntities()) {
                options.add(new QuickMuteOption(TYPE_MENTION, userMentionEntity.getScreenName()));
            }
            for (URLEntity urlEntity : status.getURLEntities()) {
                options.add(new QuickMuteOption(TYPE_URL, urlEntity.getExpandedURL()));
            }
            for (LinkMedia linkMedia : status.getMediaLinkList()) {
                options.add(new QuickMuteOption(TYPE_URL, linkMedia.getBrowseURL()));
            }
            return options.toArray(new QuickMuteOption[options.size()]);
        }
    }
}
