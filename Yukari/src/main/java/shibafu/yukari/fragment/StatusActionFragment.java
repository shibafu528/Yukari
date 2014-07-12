package shibafu.yukari.fragment;

import android.R;
import android.app.AlertDialog;
import android.app.Dialog;
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

import shibafu.yukari.activity.MuteActivity;
import shibafu.yukari.activity.StatusActivity;
import shibafu.yukari.database.MuteConfig;
import shibafu.yukari.fragment.base.ListTwitterFragment;
import shibafu.yukari.twitter.AuthUserRecord;
import shibafu.yukari.twitter.TwitterUtil;
import shibafu.yukari.twitter.statusimpl.PreformedStatus;
import twitter4j.HashtagEntity;
import twitter4j.User;

/**
 * Created by Shibafu on 13/08/02.
 */
@SuppressWarnings("deprecation")
public class StatusActionFragment extends ListTwitterFragment implements AdapterView.OnItemClickListener {
    private static final String[] ITEMS = {
            "ブラウザで開く",
            "パーマリンクをコピー",
            "ミュート",
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
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        ArrayList<String> menu = new ArrayList<>();
        menu.addAll(Arrays.asList(ITEMS));
        menu.addAll(pluginNames);

        if (user == null || status.getUser().getId() != user.NumericId) {
            menu.remove(ITEMS.length - 1);
            enableDelete = false;
        }

        setListAdapter(new ArrayAdapter<>(getActivity(), R.layout.simple_list_item_1, menu));
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
                                            getTwitterService().destroyStatus(user, status.getId());
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
                        startActivity(intent);
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

    public void onSelectedMuteOption(int which) {
        String query;
        int match = MuteConfig.MATCH_EXACT;
        PreformedStatus status = this.status.isRetweet()? this.status.getRetweetedStatus() : this.status;
        switch (which) {
            case 0:
                query = status.getText();
                break;
            case 1:
                query = status.getUser().getName();
                break;
            case 2:
                query = status.getUser().getScreenName();
                break;
            case 3:
                query = String.valueOf(status.getUser().getId());
                break;
            case 4:
                query = status.getSource();
                break;
            default:
                HashtagEntity[] hash = status.getHashtagEntities();
                if (hash.length <= 0 || which - 5 > hash.length) {
                    throw new RuntimeException("ミュートスコープ選択が不正 : " + which);
                } else {
                    query = "[#＃]" + hash[which - 5].getText();
                    which = MuteConfig.SCOPE_TEXT;
                    match = MuteConfig.MATCH_REGEX;
                }
        }
        Intent intent = new Intent(getActivity(), MuteActivity.class);
        intent.putExtra(MuteActivity.EXTRA_QUERY, query);
        intent.putExtra(MuteActivity.EXTRA_SCOPE, which);
        intent.putExtra(MuteActivity.EXTRA_MATCH, match);
        startActivity(intent);
    }

    @Override
    public void onServiceConnected() {

    }

    @Override
    public void onServiceDisconnected() {

    }

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
            User user = status.isRetweet()? status.getRetweetedStatus().getUser() : status.getUser();
            List<String> items = new ArrayList<>(Arrays.asList(new String[]{
                    "本文",
                    "ユーザ名(" + user.getName() + ")",
                    "スクリーンネーム(@" + user.getScreenName() + ")",
                    "ユーザID(" + user.getId() + ")",
                    "クライアント名(" + status.getSource() + ")"
            }));
            for (HashtagEntity hashtagEntity : status.getHashtagEntities()) {
                items.add("#" + hashtagEntity.getText());
            }

            AlertDialog dialog = new AlertDialog.Builder(getActivity())
                    .setTitle("ミュート")
                    .setItems(items.toArray(new String[items.size()]), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dismiss();
                            StatusActionFragment fragment = (StatusActionFragment) getTargetFragment();
                            if (fragment != null) {
                                fragment.onSelectedMuteOption(which);
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
}
