package shibafu.yukari.fragment;

import android.R;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.ListFragment;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import shibafu.yukari.activity.MuteActivity;
import shibafu.yukari.activity.StatusActivity;
import shibafu.yukari.service.TwitterService;
import shibafu.yukari.twitter.AuthUserRecord;
import shibafu.yukari.twitter.PreformedStatus;
import shibafu.yukari.twitter.TwitterUtil;
import twitter4j.User;

/**
 * Created by Shibafu on 13/08/02.
 */
@SuppressWarnings("deprecation")
public class StatusActionFragment extends ListFragment implements AdapterView.OnItemClickListener {
    private static final String[] ITEMS = {
            "ブラウザで開く",
            "ミュート",
            "ツイート削除"
    };

    private List<ResolveInfo> plugins;
    private List<String> pluginNames = new ArrayList<>();

    private PreformedStatus status = null;
    private AuthUserRecord user = null;

    private TwitterService service;
    private boolean serviceBound = false;

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
        }

        setListAdapter(new ArrayAdapter<>(getActivity(), R.layout.simple_list_item_1, menu));
        getListView().setOnItemClickListener(this);

        getActivity().bindService(new Intent(getActivity(), TwitterService.class), connection, Context.BIND_AUTO_CREATE);
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
                MuteMenuDialogFragment.newInstance(status, this).show(getChildFragmentManager(), "mute");
                break;
            default:
            {
                if (position == 2 && user != null && status.getUser().getId() == user.NumericId) {
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
                                            service.destroyStatus(user, status.getId());
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

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (serviceBound) {
            getActivity().unbindService(connection);
            serviceBound = false;
        }
    }

    public void onSelectedMuteOption(int which) {
        String query;
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
                throw new RuntimeException("ミュートスコープ選択が不正 : " + which);
        }
        Intent intent = new Intent(getActivity(), MuteActivity.class);
        intent.putExtra(MuteActivity.EXTRA_QUERY, query);
        intent.putExtra(MuteActivity.EXTRA_SCOPE, which);
        startActivity(intent);
    }

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            TwitterService.TweetReceiverBinder binder = (TwitterService.TweetReceiverBinder) service;
            StatusActionFragment.this.service = binder.getService();
            serviceBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
        }
    };

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
            String[] items = {
                    "本文",
                    "ユーザ名(" + user.getName() + ")",
                    "スクリーンネーム(@" + user.getScreenName() + ")",
                    "ユーザID(" + user.getId() + ")",
                    "クライアント名(" + status.getSource() + ")"
            };

            AlertDialog dialog = new AlertDialog.Builder(getActivity())
                    .setTitle("ミュート")
                    .setItems(items, new DialogInterface.OnClickListener() {
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
