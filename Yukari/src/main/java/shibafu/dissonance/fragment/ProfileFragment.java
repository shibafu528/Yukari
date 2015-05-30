package shibafu.dissonance.fragment;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.text.Html;
import android.text.util.Linkify;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import shibafu.dissonance.R;
import shibafu.dissonance.activity.MainActivity;
import shibafu.dissonance.activity.MuteActivity;
import shibafu.dissonance.activity.PreviewActivity;
import shibafu.dissonance.activity.ProfileEditActivity;
import shibafu.dissonance.activity.TweetActivity;
import shibafu.dissonance.common.Suppressor;
import shibafu.dissonance.common.TabType;
import shibafu.dissonance.common.async.ParallelAsyncTask;
import shibafu.dissonance.common.async.SimpleAsyncTask;
import shibafu.dissonance.common.async.TwitterAsyncTask;
import shibafu.dissonance.common.bitmapcache.ImageLoaderTask;
import shibafu.dissonance.database.DBUser;
import shibafu.dissonance.database.UserExtras;
import shibafu.dissonance.fragment.base.TwitterFragment;
import shibafu.dissonance.fragment.tabcontent.FriendListFragment;
import shibafu.dissonance.fragment.tabcontent.TweetListFragment;
import shibafu.dissonance.fragment.tabcontent.TweetListFragmentFactory;
import shibafu.dissonance.fragment.tabcontent.TwitterListFragment;
import shibafu.dissonance.fragment.tabcontent.UserListFragment;
import shibafu.dissonance.twitter.AuthUserRecord;
import shibafu.dissonance.util.AttrUtil;
import twitter4j.Relationship;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.URLEntity;
import twitter4j.User;

/**
 * Created by Shibafu on 13/08/10.
 */
public class ProfileFragment extends TwitterFragment implements FollowDialogFragment.FollowDialogCallback, ColorPickerDialogFragment.ColorPickerCallback{

    // TODO:画面回転とかが加わるとたまに「ユーザー情報の取得に失敗」メッセージを出力して復帰に失敗するみたい

    public static final String EXTRA_USER = "user";
    public static final String EXTRA_TARGET = "target";

    private final static long A_DAY = 24 * 60 * 60 * 1000;
    private final static SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");

    private AuthUserRecord user;
    private long targetId;
    private LoadHolder loadHolder = null;

    private boolean selfLoadId = false;
    private String selfLoadName;

    private ImageView ivProfileIcon, ivHeader;
    private ImageView ivProtected;
    private TextView tvName, tvScreenName, tvBio, tvLocation, tvWeb, tvSince, tvUserId;
    private Button btnFollowManage;
    private ImageButton ibMenu, ibSearch;
    private FrameLayout flIconBack;

    private GridView gridCommands;
    private CommandAdapter commandAdapter;

    private ProgressBar progressBar;
    private AsyncTask<Void, Void, LoadHolder> initialLoadTask = null;
    private ProfileLoader profileLoadTask;

    private LoadDialogFragment currentProgress;
    private AlertDialog currentDialog;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_profile, container, false);

        Bundle args = getArguments();
        user = (AuthUserRecord) args.getSerializable(EXTRA_USER);
        targetId = args.getLong(EXTRA_TARGET, -1);

        if (targetId < 0) {
            selfLoadId = true;
            selfLoadName = ((Uri)args.getParcelable("data")).getLastPathSegment();
        }

        progressBar = (ProgressBar) v.findViewById(R.id.progressBar);

        ivProfileIcon = (ImageView)v.findViewById(R.id.ivProfileIcon);
        ivProfileIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (loadHolder != null && loadHolder.targetUser != null) {
                    startActivity(new Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(loadHolder.targetUser.getOriginalProfileImageURLHttps()),
                            getActivity(),
                            PreviewActivity.class));
                }
            }
        });
        ivHeader = (ImageView) v.findViewById(R.id.ivProfileHeader);
        ivHeader.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (loadHolder != null
                        && loadHolder.targetUser != null
                        && loadHolder.targetUser.getProfileBannerRetinaURL() != null) {
                    startActivity(new Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(loadHolder.targetUser.getProfileBannerRetinaURL()),
                            getActivity(),
                            PreviewActivity.class));
                }
            }
        });
        ivProtected = (ImageView) v.findViewById(R.id.ivProfileProtected);
        flIconBack = (FrameLayout) v.findViewById(R.id.frameLayout);

        tvName = (TextView) v.findViewById(R.id.tvProfileName);
        tvScreenName = (TextView) v.findViewById(R.id.tvProfileScreenName);
        tvBio = (TextView) v.findViewById(R.id.tvProfileBio);
        tvLocation = (TextView) v.findViewById(R.id.tvProfileLocation);
        tvWeb = (TextView) v.findViewById(R.id.tvProfileWeb);
        tvSince = (TextView) v.findViewById(R.id.tvProfileSince);
        tvUserId = (TextView) v.findViewById(R.id.tvProfileUserId);
        tvUserId.setText("#" + targetId);

        btnFollowManage = (Button) v.findViewById(R.id.btnProfileFollow);
        btnFollowManage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                FollowDialogFragment fragment = new FollowDialogFragment();
                Bundle args = new Bundle();
                args.putSerializable(FollowDialogFragment.ARGUMENT_TARGET, loadHolder.targetUser);
                args.putSerializable(FollowDialogFragment.ARGUMENT_KNOWN_RELATIONS, new Object[]{loadHolder.relationships});
                fragment.setArguments(args);
                fragment.setTargetFragment(ProfileFragment.this, 0);
                fragment.show(getFragmentManager(), "follow");
            }
        });
        if (PreferenceManager.getDefaultSharedPreferences(getActivity()).getBoolean("allow_all_r4s", false)) {
            btnFollowManage.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    FollowDialogFragment fragment = new FollowDialogFragment();
                    Bundle args = new Bundle();
                    args.putSerializable(FollowDialogFragment.ARGUMENT_TARGET, loadHolder.targetUser);
                    args.putSerializable(FollowDialogFragment.ARGUMENT_KNOWN_RELATIONS, new Object[]{loadHolder.relationships});
                    args.putSerializable(FollowDialogFragment.ARGUMENT_ALL_R4S, true);
                    fragment.setArguments(args);
                    fragment.setTargetFragment(ProfileFragment.this, 0);
                    fragment.show(getFragmentManager(), "follow");
                    return true;
                }
            });
        }

        ibMenu = (ImageButton) v.findViewById(R.id.ibProfileMenu);
        ibMenu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                List<String> menuList = new ArrayList<>();
                menuList.add("ツイートを送る");
                menuList.add("DMを送る");
                menuList.add("ブラウザで開く");
                menuList.add("保存しているリスト");
                menuList.add("追加されているリスト");
                menuList.add("リストへ追加/削除");
                menuList.add("ミュートする");
                menuList.add("カラーラベルを設定");

                if ((loadHolder != null && loadHolder.targetUser.getId() == user.NumericId) ||
                        (targetId >= 0 && targetId == user.NumericId) ||
                        (selfLoadId && selfLoadName.equals(user.ScreenName))) {
                    menuList.add("プロフィール編集");
                    menuList.add("ブロックリスト");
                }

                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                builder.setItems(menuList.toArray(new String[menuList.size()]), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        switch (which) {
                            case 0:
                            {
                                Intent intent = new Intent(getActivity(), TweetActivity.class);
                                intent.putExtra(TweetActivity.EXTRA_USER, user);
                                intent.putExtra(TweetActivity.EXTRA_MODE, TweetActivity.MODE_REPLY);
                                intent.putExtra(TweetActivity.EXTRA_TEXT, "@" + loadHolder.targetUser.getScreenName() + " ");
                                startActivity(intent);
                                break;
                            }
                            case 1:
                            {
                                Intent intent = new Intent(getActivity(), TweetActivity.class);
                                intent.putExtra(TweetActivity.EXTRA_MODE, TweetActivity.MODE_DM);
                                intent.putExtra(TweetActivity.EXTRA_IN_REPLY_TO, loadHolder.targetUser.getId());
                                intent.putExtra(TweetActivity.EXTRA_DM_TARGET_SN, loadHolder.targetUser.getScreenName());
                                startActivity(intent);
                                break;
                            }
                            case 2:
                            {
                                Intent intent = new Intent(Intent.ACTION_VIEW,
                                        Uri.parse("http://twitter.com/" + loadHolder.targetUser.getScreenName()));
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(intent);
                                break;
                            }
                            case 3:
                            {
                                UserListFragment fragment = new UserListFragment();
                                Bundle args = new Bundle();
                                args.putInt(TwitterListFragment.EXTRA_MODE, UserListFragment.MODE_FOLLOWING);
                                args.putSerializable(TweetListFragment.EXTRA_USER, user);
                                args.putSerializable(TweetListFragment.EXTRA_SHOW_USER, loadHolder.targetUser);
                                args.putString(TweetListFragment.EXTRA_TITLE, "Lists: @" + loadHolder.targetUser.getScreenName());
                                fragment.setArguments(args);
                                FragmentTransaction transaction = getFragmentManager().beginTransaction();
                                transaction.replace(R.id.frame, fragment, "contain");
                                transaction.addToBackStack(null);
                                transaction.commit();
                                break;
                            }
                            case 4:
                            {
                                UserListFragment fragment = new UserListFragment();
                                Bundle args = new Bundle();
                                args.putInt(TwitterListFragment.EXTRA_MODE, UserListFragment.MODE_MEMBERSHIP);
                                args.putSerializable(TweetListFragment.EXTRA_USER, user);
                                args.putSerializable(TweetListFragment.EXTRA_SHOW_USER, loadHolder.targetUser);
                                args.putString(TweetListFragment.EXTRA_TITLE, "Listed: @" + loadHolder.targetUser.getScreenName());
                                fragment.setArguments(args);
                                FragmentTransaction transaction = getFragmentManager().beginTransaction();
                                transaction.replace(R.id.frame, fragment, "contain");
                                transaction.addToBackStack(null);
                                transaction.commit();
                                break;
                            }
                            case 5:
                            {
                                ListRegisterDialogFragment dialogFragment =
                                        ListRegisterDialogFragment.newInstance(loadHolder.targetUser);
                                dialogFragment.setTargetFragment(ProfileFragment.this, 1);
                                dialogFragment.show(getChildFragmentManager(), "list");
                                break;
                            }
                            case 6:
                            {
                                MuteMenuDialogFragment dialogFragment =
                                        MuteMenuDialogFragment.newInstance(loadHolder.targetUser, ProfileFragment.this);
                                dialogFragment.show(getChildFragmentManager(), "mute");
                                break;
                            }
                            case 7:
                            {
                                ColorPickerDialogFragment dialogFragment =
                                        ColorPickerDialogFragment.newInstance(
                                                getTargetUserColor(), "colorLabel"
                                        );
                                dialogFragment.setTargetFragment(ProfileFragment.this, 2);
                                dialogFragment.show(getChildFragmentManager(), "colorLabel");
                                break;
                            }
                            case 8:
                            {
                                Intent intent = new Intent(getActivity(), ProfileEditActivity.class);
                                intent.putExtra(ProfileEditActivity.EXTRA_USER, user);
                                startActivity(intent);
                                break;
                            }
                            case 9:
                            {
                                FriendListFragment fragment = new FriendListFragment();
                                Bundle args = new Bundle();
                                args.putInt(FriendListFragment.EXTRA_MODE, FriendListFragment.MODE_BLOCKING);
                                args.putSerializable(TweetListFragment.EXTRA_USER, user);
                                args.putSerializable(TweetListFragment.EXTRA_SHOW_USER, loadHolder.targetUser);
                                args.putString(TweetListFragment.EXTRA_TITLE, "Blocking: @" + loadHolder.targetUser.getScreenName());
                                fragment.setArguments(args);
                                FragmentTransaction transaction = getFragmentManager().beginTransaction();
                                transaction.replace(R.id.frame, fragment, "contain");
                                transaction.addToBackStack(null);
                                transaction.commit();
                                break;
                            }
                        }
                        currentDialog = null;
                    }
                });
                builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        dialog.dismiss();
                        currentDialog = null;
                    }
                });
                AlertDialog ad = builder.create();
                ad.show();
                currentDialog = ad;
            }
        });
        ibSearch = (ImageButton) v.findViewById(R.id.ibProfileSearch);
        ibSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (loadHolder != null && loadHolder.targetUser != null) {
                    Intent intent = new Intent(getActivity(), MainActivity.class);
                    intent.putExtra(MainActivity.EXTRA_SEARCH_WORD, "@" + loadHolder.targetUser.getScreenName());
                    startActivity(intent);
                }
            }
        });

        gridCommands = (GridView) v.findViewById(R.id.gvProfileCommands);
        gridCommands.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position < 0 || position > 3) return;
                Fragment fragment = null;
                Bundle args = new Bundle();
                switch (position) {
                    case 0:
                    {
                        fragment = TweetListFragmentFactory.newInstance(TabType.TABTYPE_USER);
                        args.putInt(TweetListFragment.EXTRA_MODE, TabType.TABTYPE_USER);
                        args.putSerializable(TweetListFragment.EXTRA_USER, user);
                        args.putSerializable(TweetListFragment.EXTRA_SHOW_USER, loadHolder.targetUser);
                        args.putString(TweetListFragment.EXTRA_TITLE, "Tweets: @" + loadHolder.targetUser.getScreenName());
                        break;
                    }
                    case 1:
                    {
                        fragment = TweetListFragmentFactory.newInstance(TabType.TABTYPE_FAVORITE);
                        args.putInt(TweetListFragment.EXTRA_MODE, TabType.TABTYPE_FAVORITE);
                        args.putSerializable(TweetListFragment.EXTRA_USER, user);
                        args.putSerializable(TweetListFragment.EXTRA_SHOW_USER, loadHolder.targetUser);
                        args.putString(TweetListFragment.EXTRA_TITLE, "Favorites: @" + loadHolder.targetUser.getScreenName());
                        break;
                    }
                    case 2:
                    {
                        fragment = new FriendListFragment();
                        args.putInt(FriendListFragment.EXTRA_MODE, FriendListFragment.MODE_FRIEND);
                        args.putSerializable(TweetListFragment.EXTRA_USER, user);
                        args.putSerializable(TweetListFragment.EXTRA_SHOW_USER, loadHolder.targetUser);
                        args.putString(TweetListFragment.EXTRA_TITLE, "Follow: @" + loadHolder.targetUser.getScreenName());
                        break;
                    }
                    case 3:
                    {
                        fragment = new FriendListFragment();
                        args.putInt(FriendListFragment.EXTRA_MODE, FriendListFragment.MODE_FOLLOWER);
                        args.putSerializable(TweetListFragment.EXTRA_USER, user);
                        args.putSerializable(TweetListFragment.EXTRA_SHOW_USER, loadHolder.targetUser);
                        args.putString(TweetListFragment.EXTRA_TITLE, "Follower: @" + loadHolder.targetUser.getScreenName());
                        break;
                    }
                }
                if (fragment != null) {
                    fragment.setArguments(args);
                    FragmentTransaction transaction = getFragmentManager().beginTransaction();
                    transaction.replace(R.id.frame, fragment, "contain");
                    transaction.addToBackStack(null);
                    transaction.commit();
                }
            }
        });

        return v;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        List<Command> commands = new ArrayList<>();
        commands.add(new Command(AttrUtil.resolveAttribute(getActivity().getTheme(),
                R.attr.profileTweetsDrawable), "Tweets", "0"));
        commands.add(new Command(AttrUtil.resolveAttribute(getActivity().getTheme(),
                R.attr.profileFavoritesDrawable), "Favorites", "0"));
        commands.add(new Command(AttrUtil.resolveAttribute(getActivity().getTheme(),
                R.attr.profileFollowsDrawable), "Follows", "0"));
        commands.add(new Command(AttrUtil.resolveAttribute(getActivity().getTheme(),
                R.attr.profileFollowersDrawable), "Followers", "0"));

        commandAdapter = new CommandAdapter(getActivity(), commands);
        gridCommands.setAdapter(commandAdapter);

        if (savedInstanceState != null) {
            loadHolder = savedInstanceState.getParcelable("loadHolder");
        }

        if (loadHolder != null) {
            showProfile(loadHolder);
            if (loadHolder.relationships == null) {
                new RelationLoader().execute();
            }
        }
        else {
            final ParallelAsyncTask<Void, Void, LoadHolder> task = new ParallelAsyncTask<Void, Void, LoadHolder>() {
                @Override
                protected LoadHolder doInBackground(Void... params) {
                    while (!isTwitterServiceBound()) {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }

                    DBUser user;
                    if (selfLoadId) {
                        String name = selfLoadName;
                        if (name.startsWith("@")) {
                            name = name.substring(1);
                        }

                        user = getTwitterService().getDatabase().getUser(name);
                    } else {
                        user = getTwitterService().getDatabase().getUser(targetId);
                    }

                    if (ProfileFragment.this.user == null) {
                        ProfileFragment.this.user = getTwitterService().getPrimaryUser();
                    }
                    if (user != null) {
                        return new LoadHolder(user, null);
                    }
                    return null;
                }

                @Override
                protected void onPostExecute(LoadHolder holder) {
                    profileLoadTask = new ProfileLoader(getActivity().getApplicationContext());
                    profileLoadTask.execute();
                    if (holder != null) {
                        if (currentProgress != null) {
                            currentProgress.dismiss();
                            currentProgress = null;
                        }
                        showProfile(holder);
                    }
                    initialLoadTask = null;
                }
            };

            currentProgress = new LoadDialogFragment();
            currentProgress.setTargetFragment(ProfileFragment.this, 0);
            currentProgress.show(getFragmentManager(), "Loading");

            initialLoadTask = task;
            task.executeParallel();

            btnFollowManage.setEnabled(false);
            btnFollowManage.setText("読み込み中...");
        }
    }

    private void showProfile(LoadHolder holder) {
        if (holder == null) {
            Toast.makeText(getActivity(), "ユーザー情報の取得に失敗しました", Toast.LENGTH_SHORT).show();
            getActivity().finish();
            return;
        }
        this.loadHolder = holder;
        if (loadHolder.targetUser != null && loadHolder.relationships != null) {
            progressBar.setVisibility(View.INVISIBLE);
        }

        if (isTwitterServiceBound()) {
            List<AuthUserRecord> users = getTwitterService().getUsers();
            for (AuthUserRecord usr : users) {
                if (usr.NumericId == holder.targetUser.getId()) {
                    user = usr;
                    break;
                }
            }
        }

        ImageLoaderTask.loadProfileIcon(getActivity().getApplicationContext(), ivProfileIcon, holder.targetUser.getBiggerProfileImageURLHttps());
        if (loadHolder.targetUser.getProfileBannerMobileURL() != null) {
            ImageLoaderTask.loadBitmap(getActivity().getApplicationContext(), ivHeader, holder.targetUser.getProfileBannerMobileURL());
        }

        if (holder.targetUser.isProtected()) {
            ivProtected.setVisibility(View.VISIBLE);
        }
        else {
            ivProtected.setVisibility(View.GONE);
        }

        Log.d("ProfileFragment", "header url: " + holder.targetUser.getProfileBannerMobileURL());
        tvName.setText(holder.targetUser.getName());
        tvScreenName.setText("@" + holder.targetUser.getScreenName());

        //Bioはエスケープや短縮の展開を行う
        {
            String bio = holder.targetUser.getDescription();
            if (bio == null) {
                bio = "";
            }

            //TODO: リンクがうまくはれてないことがしばしばあるよ

            //URLを展開する
            URLEntity[] urlEntities = holder.targetUser.getDescriptionURLEntities();
            if (urlEntities != null) for (URLEntity entity : urlEntities) {
                bio = bio.replace(entity.getURL(), entity.getExpandedURL());
            }

            //改行コードをBRタグにする
            bio = bio.replaceAll("\\r\\n", "<br>").replaceAll("\\n", "<br>");

            //エスケープしてテキストを表示
            tvBio.setText(Html.fromHtml(bio).toString());
            Linkify.addLinks(tvBio, Linkify.WEB_URLS);
            Log.d("ProfileFragment", "Profile: " + tvBio.getText());

            //ScreenNameに対するリンク張り
            Pattern pattern = Pattern.compile("@[a-zA-Z0-9_]{1,15}");
            String jumpUrl = "content://shibafu.yukari.link/user/";
            Linkify.addLinks(tvBio, pattern, jumpUrl);
            //Hashtagに対するリンク張り
            pattern = Pattern.compile(
                    "(?:#|\\uFF03)([a-zA-Z0-9_\\u3041-\\u3094\\u3099-\\u309C\\u30A1-\\u30FA\\u3400-\\uD7FF\\uFF10-\\uFF19\\uFF20-\\uFF3A\\uFF41-\\uFF5A\\uFF66-\\uFF9E]+)");
            final String jumpUrlHash = "content://shibafu.yukari.link/hash/";
            Linkify.TransformFilter filter = new Linkify.TransformFilter() {
                @Override
                public String transformUrl(Matcher match, String url) {
                    return jumpUrlHash + match.group(match.groupCount());
                }
            };
            Linkify.addLinks(tvBio, pattern, jumpUrlHash, null, filter);
        }

        tvLocation.setText(holder.targetUser.getLocation());
        tvWeb.setText(holder.targetUser.getURLEntity().getExpandedURL());
        {
            String dateStr = sdf.format(holder.targetUser.getCreatedAt());
            int totalDay = (int) ((System.currentTimeMillis() - holder.targetUser.getCreatedAt().getTime()) / A_DAY);
            float tpd = (float)holder.targetUser.getStatusesCount() / totalDay;

            tvSince.setText(String.format("%s (%d日, %.2ftweet/day)", dateStr, totalDay, tpd));
        }
        tvUserId.setText("#" + holder.targetUser.getId());

        commandAdapter.getItem(0).strBottom = String.valueOf(holder.targetUser.getStatusesCount());
        commandAdapter.getItem(1).strBottom = String.valueOf(holder.targetUser.getFavouritesCount());
        commandAdapter.getItem(2).strBottom = String.valueOf(holder.targetUser.getFriendsCount());
        commandAdapter.getItem(3).strBottom = String.valueOf(holder.targetUser.getFollowersCount());
        commandAdapter.notifyDataSetChanged();

        flIconBack.setBackgroundColor(getTargetUserColor());
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable("loadHolder", loadHolder);
    }

    @Override
    public void onResume() {
        super.onResume();
        getActivity().setTitle("プロフィール");

        if (currentDialog != null) {
            currentDialog.show();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (initialLoadTask != null) {
            initialLoadTask.cancel(true);
        }
        if (profileLoadTask != null) {
            profileLoadTask.cancel(true);
        }
    }

    public void onCancelledLoading() {
        if (initialLoadTask != null) {
            initialLoadTask.cancel(true);
        }
        getActivity().finish();
    }

    private int getTargetUserColor() {
        if (isTwitterServiceBound() && getTwitterService() != null) {
            for (UserExtras userExtra : getTwitterService().getUserExtras()) {
                if (userExtra.getId() == loadHolder.targetUser.getId()) {
                    return userExtra.getColor();
                }
            }
        }
        return 0;
    }

    @Override
    public void onChangedRelationships(final List<FollowDialogFragment.RelationClaim> claims) {
        new AsyncTask<Void, Void, String>() {

            private UpdateDialogFragment updateDialogFragment;

            @Override
            protected void onPreExecute() {
                updateDialogFragment = new UpdateDialogFragment();
                updateDialogFragment.setCancelable(false);
                updateDialogFragment.show(getFragmentManager(), "follow");
            }

            @Override
            protected String doInBackground(Void... voids) {
                StringBuilder sb = new StringBuilder();
                for (FollowDialogFragment.RelationClaim claim : claims) {
                    if (sb.length() > 0) {
                        sb.append("\n");
                    }
                    sb.append("@");
                    sb.append(claim.getSourceAccount().ScreenName);
                    sb.append(": ");

                    //サービスがバインドされていない場合は待機する
                    while (!isTwitterServiceBound()) {
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }

                    Suppressor suppressor = getTwitterService().getSuppressor();

                    Twitter twitter = getTwitterService().getTwitter();
                    twitter.setOAuthAccessToken(claim.getSourceAccount().getAccessToken());

                    switch (claim.getNewRelation()) {
                        case FollowDialogFragment.RELATION_NONE:
                            try {
                                twitter.destroyFriendship(claim.getTargetUser());
                                sb.append("リムーブしました");
                            } catch (TwitterException e) {
                                e.printStackTrace();
                                sb.append("リムーブ失敗:").append(e.getStatusCode());
                            }
                            break;
                        case FollowDialogFragment.RELATION_FOLLOW:
                            try {
                                twitter.createFriendship(claim.getTargetUser());
                                sb.append("フォローしました");
                            } catch (TwitterException e) {
                                e.printStackTrace();
                                sb.append("フォロー失敗:").append(e.getStatusCode());
                            }
                            break;
                        case FollowDialogFragment.RELATION_PRE_R4S:
                            try {
                                twitter.reportSpam(claim.getTargetUser());
                                sb.append("スパム報告しました");
                            } catch (TwitterException e) {
                                e.printStackTrace();
                                sb.append("スパム報告失敗:").append(e.getStatusCode());
                            }
                            break;
                        case FollowDialogFragment.RELATION_BLOCK:
                            try {
                                twitter.createBlock(claim.getTargetUser());
                                suppressor.addBlockedIDs(new long[]{claim.getTargetUser()});
                                sb.append("ブロックしました");
                            } catch (TwitterException e) {
                                e.printStackTrace();
                                sb.append("ブロック失敗:").append(e.getStatusCode());
                            }
                            break;
                        case FollowDialogFragment.RELATION_UNBLOCK:
                            try {
                                twitter.destroyBlock(claim.getTargetUser());
                                suppressor.removeBlockedID(claim.getTargetUser());
                                sb.append("ブロック解除しました");
                            } catch (TwitterException e) {
                                e.printStackTrace();
                                sb.append("ブロック解除失敗:").append(e.getStatusCode());
                            }
                            break;
                        case FollowDialogFragment.RELATION_CUTOFF:
                            try {
                                twitter.createBlock(claim.getTargetUser());
                                sb.append("ブロックしました");
                                try {
                                    twitter.destroyBlock(claim.getTargetUser());
                                    sb.append("ブロック解除しました");
                                } catch (TwitterException e) {
                                    e.printStackTrace();
                                    sb.append("ブロック解除失敗:").append(e.getStatusCode());
                                }
                            } catch (TwitterException e) {
                                e.printStackTrace();
                                sb.append("ブロック失敗:").append(e.getStatusCode());
                            }
                            break;
                    }

                    //フォロー情報をリロードする
                    try {
                        loadHolder.relationships.put(claim.getSourceAccount(), twitter.showFriendship(claim.getSourceAccount().NumericId, claim.getTargetUser()));
                    } catch (TwitterException e) {
                        e.printStackTrace();
                    }
                }
                return sb.toString();
            }

            @Override
            protected void onPostExecute(String string) {
                updateDialogFragment.close();
                if (string != null && !string.equals("")) {
                    Toast.makeText(getActivity(), string, Toast.LENGTH_LONG).show();
                }
            }
        }.execute();
    }

    public void onSelectedMuteOption(int which) {
        String query;
        switch (which) {
            case 1:
                query = loadHolder.targetUser.getName();
                break;
            case 2:
                query = loadHolder.targetUser.getScreenName();
                break;
            case 3:
                query = String.valueOf(loadHolder.targetUser.getId());
                break;
            default:
                throw new RuntimeException("ミュートスコープ選択が不正 : " + which);
        }
        Intent intent = new Intent(getActivity(), MuteActivity.class);
        intent.putExtra(MuteActivity.EXTRA_QUERY, query);
        intent.putExtra(MuteActivity.EXTRA_SCOPE, which);
        startActivity(intent);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        List<Fragment> fragments = getChildFragmentManager().getFragments();
        if (fragments != null) {
            for (Fragment fragment : fragments) {
                fragment.onActivityResult(requestCode, resultCode, data);
            }
        }
    }

    @Override
    public void onServiceConnected() {

    }

    @Override
    public void onServiceDisconnected() {

    }

    @Override
    public void onColorPicked(int color, String tag) {
        if (isTwitterServiceBound()) {
            getTwitterService().setColor(loadHolder.targetUser.getId(), color);
            showProfile(loadHolder);
        } else {
            //TODO: 遅延処理にすべきかなあ
            Toast.makeText(getActivity().getApplicationContext(), "カラーラベル設定に失敗しました", Toast.LENGTH_SHORT).show();
        }
    }

    private class Command {
        public int iconId;
        public String strTop;
        public String strBottom;

        private Command(int iconId, String strTop, String strBottom) {
            this.iconId = iconId;
            this.strTop = strTop;
            this.strBottom = strBottom;
        }
    }

    private class CommandAdapter extends ArrayAdapter<Command> {

        public CommandAdapter(Context context, List<Command> objects) {
            super(context, R.layout.view_2linebutton, objects);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;

            if (v == null) {
                LayoutInflater inflater = (LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                v = inflater.inflate(R.layout.view_2linebutton, null);
            }

            Command c = getItem(position);
            if (c != null) {
                ImageView ivIcon = (ImageView) v.findViewById(R.id.lineButtonImage);
                ivIcon.setImageResource(c.iconId);

                TextView tvTop = (TextView) v.findViewById(R.id.lineButtonText);
                tvTop.setText(c.strTop);
                TextView tvBottom = (TextView) v.findViewById(R.id.lineButtonCount);
                tvBottom.setText(c.strBottom);
            }

            return v;
        }
    }

    private static class LoadHolder implements Parcelable{
        User targetUser;
        LinkedHashMap<AuthUserRecord, Relationship> relationships;

        private LoadHolder(User targetUser, LinkedHashMap<AuthUserRecord, Relationship> relationships) {
            this.targetUser = targetUser;
            this.relationships = relationships;
        }

        private LoadHolder(Parcel in) {
            //targetUserの入力
            targetUser = (User) in.readSerializable();
            //マップ要素数の入力
            int mapSize = in.readInt();
            //マップ要素の復元
            if (mapSize > -1) {
                relationships = new LinkedHashMap<>(mapSize);
                AuthUserRecord userRecord;
                Relationship relationship;
                for (int i = 0; i < mapSize; ++i) {
                    userRecord = (AuthUserRecord) in.readSerializable();
                    relationship = (Relationship) in.readSerializable();
                    relationships.put(userRecord, relationship);
                }
            }
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel out, int i) {
            //targetUserの出力
            out.writeSerializable(targetUser);
            //マップ内要素数を出力
            if (relationships == null) {
                out.writeInt(-1);
            }
            else {
                out.writeInt(relationships.size());
                //K,V,K,...の順番でマップ内要素を出力
                for (AuthUserRecord userRecord : relationships.keySet()) {
                    out.writeSerializable(userRecord);
                    out.writeSerializable(relationships.get(userRecord));
                }
            }
        }

        public static final Creator<LoadHolder> CREATOR = new Creator<LoadHolder>() {
            @Override
            public LoadHolder createFromParcel(Parcel parcel) {
                return new LoadHolder(parcel);
            }

            @Override
            public LoadHolder[] newArray(int i) {
                return new LoadHolder[i];
            }
        };
    }

    public static class LoadDialogFragment extends DialogFragment {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            ProgressDialog pd = new ProgressDialog(getActivity());
            pd.setMessage("読み込み中...");
            pd.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            pd.setIndeterminate(true);
            return pd;
        }

        @Override
        public void onCancel(DialogInterface dialog) {
            super.onCancel(dialog);
            ((ProfileFragment)getTargetFragment()).onCancelledLoading();
        }
    }

    public static class UpdateDialogFragment extends DialogFragment {
        private boolean dismissRequest;

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            ProgressDialog pd = new ProgressDialog(getActivity());
            pd.setMessage("フォロー状態を更新中...");
            pd.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            pd.setIndeterminate(true);
            pd.setCanceledOnTouchOutside(false);
            return pd;
        }

        @Override
        public void onResume() {
            super.onResume();
            if (dismissRequest) {
                dismiss();
            }
        }

        public void close() {
            if (isResumed()) {
                dismiss();
            }
            else {
                dismissRequest = true;
            }
        }
    }

    private class ProfileLoader extends TwitterAsyncTask<Void> {

        protected ProfileLoader(Context context) {
            super(context);
        }

        @Override
        protected TwitterException doInBackground(Void... voids) {
            if (!isTwitterServiceBound()) {
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if (!isTwitterServiceBound()) {
                    return null;
                }
            }

            try {
                User user;
                if (selfLoadId) {
                    String name = selfLoadName;
                    if (name.startsWith("@")) {
                        name = name.substring(1);
                    }
                    user = getTwitterService().getTwitter().showUser(name);
                } else {
                    user = getTwitterService().getTwitter().showUser(targetId);
                }

                if (user != null) {
                    getTwitterService().getDatabase().updateRecord(new DBUser(user));
                    if (loadHolder == null) {
                        loadHolder = new LoadHolder(user, null);
                    }
                    else {
                        loadHolder.targetUser = user;
                    }
                }
                else {
                    loadHolder = null;
                }
            } catch (TwitterException e) {
                return e;
            }
            return null;
        }

        @Override
        protected void onPostExecute(TwitterException e) {
            if (currentProgress != null) {
                currentProgress.dismiss();
                currentProgress = null;
            }

            if (e != null) {
                Toast.makeText(getActivity(), "ユーザー情報の取得に失敗しました\n\n" + e.getErrorMessage(), Toast.LENGTH_LONG).show();
                getActivity().finish();
            }
            else {
                showProfile(loadHolder);
            }

            if (loadHolder != null) {
                new RelationLoader().executeParallel();
            }

            profileLoadTask = null;
        }
    }

    private class RelationLoader extends SimpleAsyncTask {

        @Override
        protected Void doInBackground(Void... voids) {
            if (!isTwitterServiceBound()) {
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if (!isTwitterServiceBound()) {
                    return null;
                }
            }

            LinkedHashMap<AuthUserRecord, Relationship> relationships = new LinkedHashMap<>();
            List<AuthUserRecord> users =
                    getTwitterService() != null ?
                            getTwitterService().getUsers() != null ?
                                    getTwitterService().getUsers()
                                    : new ArrayList<AuthUserRecord>()
                            : new ArrayList<AuthUserRecord>();
            for (AuthUserRecord userRecord : users) {
                try {
                    relationships.put(userRecord,
                            getTwitterService().getTwitter().showFriendship(userRecord.NumericId, loadHolder.targetUser.getId()));
                } catch (TwitterException e) {
                    e.printStackTrace();
                }
            }
            loadHolder.relationships = relationships;
            return null;
        }

        @Override
        protected void onPreExecute() {
            progressBar.setVisibility(View.VISIBLE);
            btnFollowManage.setEnabled(false);
            btnFollowManage.setText("読み込み中...");
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            progressBar.setVisibility(View.INVISIBLE);
            btnFollowManage.setEnabled(true);
            btnFollowManage.setText("フォロー管理");
        }
    }

    public static class MuteMenuDialogFragment extends DialogFragment {

        public static MuteMenuDialogFragment newInstance(User user, ProfileFragment target) {
            MuteMenuDialogFragment fragment = new MuteMenuDialogFragment();
            Bundle args = new Bundle();
            args.putSerializable("user", user);
            fragment.setArguments(args);
            fragment.setTargetFragment(target, 0);
            return fragment;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            Bundle args = getArguments();
            User user = (User) args.getSerializable("user");
            String[] items = {
                    "ユーザ名(" + user.getName() + ")",
                    "スクリーンネーム(@" + user.getScreenName() + ")",
                    "ユーザID(" + user.getId() + ")"
            };

            AlertDialog dialog = new AlertDialog.Builder(getActivity())
                    .setTitle("ミュート")
                    .setItems(items, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dismiss();
                            ProfileFragment fragment = (ProfileFragment) getTargetFragment();
                            if (fragment != null) {
                                fragment.onSelectedMuteOption(which+1);
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
