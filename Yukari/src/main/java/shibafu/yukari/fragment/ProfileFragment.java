package shibafu.yukari.fragment;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.appbar.AppBarLayout;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.Toolbar;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.URLSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import shibafu.yukari.R;
import shibafu.yukari.activity.AccountChooserActivity;
import shibafu.yukari.activity.MainActivity;
import shibafu.yukari.activity.MuteActivity;
import shibafu.yukari.activity.PreviewActivity2;
import shibafu.yukari.activity.ProfileActivity;
import shibafu.yukari.activity.ProfileEditActivity;
import shibafu.yukari.activity.TweetActivity;
import shibafu.yukari.common.Suppressor;
import shibafu.yukari.common.TabType;
import shibafu.yukari.common.async.ParallelAsyncTask;
import shibafu.yukari.common.async.SimpleAsyncTask;
import shibafu.yukari.common.async.TwitterAsyncTask;
import shibafu.yukari.common.bitmapcache.ImageLoaderTask;
import shibafu.yukari.common.span.HashTagSpan;
import shibafu.yukari.common.span.UserProfileSpan;
import shibafu.yukari.database.AccountManager;
import shibafu.yukari.database.CentralDatabase;
import shibafu.yukari.database.DBUser;
import shibafu.yukari.database.Provider;
import shibafu.yukari.database.UserExtras;
import shibafu.yukari.database.UserExtrasManager;
import shibafu.yukari.fragment.base.YukariBaseFragment;
import shibafu.yukari.fragment.tabcontent.TimelineFragment;
import shibafu.yukari.fragment.tabcontent.TweetListFragmentFactory;
import shibafu.yukari.service.TwitterService;
import shibafu.yukari.database.AuthUserRecord;
import shibafu.yukari.twitter.TwitterUtil;
import shibafu.yukari.twitter.entity.TwitterUser;
import shibafu.yukari.view.ProfileButton;
import twitter4j.Relationship;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.URLEntity;
import twitter4j.User;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Queue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Shibafu on 13/08/10.
 */
public class ProfileFragment extends YukariBaseFragment implements FollowDialogFragment.FollowDialogCallback, ColorPickerDialogFragment.ColorPickerCallback, Toolbar.OnMenuItemClickListener {

    // TODO:画面回転とかが加わるとたまに「ユーザー情報の取得に失敗」メッセージを出力して復帰に失敗するみたい

    public static final String EXTRA_USER = "user";
    public static final String EXTRA_TARGET = "target";

    private static final int REQUEST_PRIORITY_ACCOUNT = 1;

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
    private ProfileButton pbTweets, pbFavorites, pbFollows, pbFollowers;
    private Button btnFollowManage, btnOwakareBlock;
    private ImageView ivUserColor;
    private Toolbar toolbar;
    private AppBarLayout appBarLayout;
    private View headerLayout;

    private View progressBar;
    private AsyncTask<Void, Void, LoadHolder> initialLoadTask = null;
    private ProfileLoader profileLoadTask;

    private LoadDialogFragment currentProgress;

    // Service接続前にそれを必要とする処理に当たったときに後回しにするキュー
    private Queue<Runnable> serviceTasks = new LinkedList<>();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = getArguments();
        user = (AuthUserRecord) args.getSerializable(EXTRA_USER);
        targetId = args.getLong(EXTRA_TARGET, -1);

        if (targetId < 0) {
            selfLoadId = true;
            selfLoadName = ((Uri)args.getParcelable("data")).getLastPathSegment();
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_profile, container, false);

        toolbar = (Toolbar) v.findViewById(R.id.toolbar);
        toolbar.inflateMenu(R.menu.profile);
        toolbar.setOnMenuItemClickListener(this);

        progressBar = v.findViewById(R.id.progressBar);

        ivProfileIcon = (ImageView)v.findViewById(R.id.ivProfileIcon);
        ivProfileIcon.setOnClickListener(v1 -> {
            if (loadHolder != null && loadHolder.targetUser != null) {
                startActivity(PreviewActivity2.newIntent(requireContext(), Uri.parse(loadHolder.targetUser.getOriginalProfileImageURLHttps())));
            }
        });
        ivHeader = (ImageView) v.findViewById(R.id.ivProfileHeader);
        ivHeader.setOnClickListener(v1 -> {
            if (loadHolder != null
                    && loadHolder.targetUser != null
                    && loadHolder.targetUser.getProfileBannerRetinaURL() != null) {
                startActivity(PreviewActivity2.newIntent(requireContext(), Uri.parse(loadHolder.targetUser.getProfileBannerRetinaURL())));
            }
        });
        ivProtected = (ImageView) v.findViewById(R.id.ivProfileProtected);
        ivUserColor = (ImageView) v.findViewById(R.id.ivProfileUserColor);

        tvName = (TextView) v.findViewById(R.id.tvProfileName);
        tvScreenName = (TextView) v.findViewById(R.id.tvProfileScreenName);
        tvBio = (TextView) v.findViewById(R.id.tvProfileBio);
        tvLocation = (TextView) v.findViewById(R.id.tvProfileLocation);
        tvWeb = (TextView) v.findViewById(R.id.tvProfileWeb);
        tvSince = (TextView) v.findViewById(R.id.tvProfileSince);
        tvUserId = (TextView) v.findViewById(R.id.tvProfileUserId);
        tvUserId.setText("#" + targetId);

        pbTweets = v.findViewById(R.id.cvProfileTweets);
        pbTweets.setOnClickListener(view -> {
            Fragment fragment = TweetListFragmentFactory.newInstance(TabType.TABTYPE_FILTER);
            Bundle args1 = new Bundle();

            args1.putInt(TimelineFragment.EXTRA_MODE, TabType.TABTYPE_FILTER);
            args1.putSerializable(TimelineFragment.EXTRA_USER, user);
            args1.putSerializable(TimelineFragment.EXTRA_SHOW_USER, loadHolder.targetUser);
            args1.putString(TimelineFragment.EXTRA_TITLE, "Tweets: @" + loadHolder.targetUser.getScreenName());
            args1.putString(TimelineFragment.EXTRA_FILTER_QUERY, String.format("from user:\"%s/%s\"", user.ScreenName, loadHolder.targetUser.getScreenName()));

            fragment.setArguments(args1);

            FragmentTransaction transaction = getFragmentManager().beginTransaction();
            transaction.replace(R.id.frame, fragment, ProfileActivity.FRAGMENT_TAG_CONTENT);
            transaction.addToBackStack(null);
            transaction.commit();
        });
        pbTweets.setOnLongClickListener(view -> {
            PopupMenu menu = new PopupMenu(getContext(), view);
            menu.getMenu().add(Menu.NONE, 0, Menu.NONE, "画像付きツイートを表示");
            menu.setOnMenuItemClickListener(item -> {
                switch (item.getItemId()) {
                    case 0: {
                        Fragment fragment = TweetListFragmentFactory.newInstance(TabType.TABTYPE_FILTER);
                        Bundle args1 = new Bundle();

                        args1.putInt(TimelineFragment.EXTRA_MODE, TabType.TABTYPE_FILTER);
                        args1.putSerializable(TimelineFragment.EXTRA_USER, user);
                        args1.putSerializable(TimelineFragment.EXTRA_SHOW_USER, loadHolder.targetUser);
                        args1.putString(TimelineFragment.EXTRA_TITLE, "Media: @" + loadHolder.targetUser.getScreenName());
                        args1.putString(TimelineFragment.EXTRA_FILTER_QUERY, String.format("from user:\"%s/%s\" where (neq ?media.empty)", user.ScreenName, loadHolder.targetUser.getScreenName()));

                        fragment.setArguments(args1);

                        FragmentTransaction transaction = getFragmentManager().beginTransaction();
                        transaction.replace(R.id.frame, fragment, ProfileActivity.FRAGMENT_TAG_CONTENT);
                        transaction.addToBackStack(null);
                        transaction.commit();
                        return true;
                    }
                }
                return false;
            });
            menu.show();
            return true;
        });
        pbFavorites = v.findViewById(R.id.cvProfileFavorites);
        pbFavorites.setOnClickListener(view -> {
            Fragment fragment = TweetListFragmentFactory.newInstance(TabType.TABTYPE_FILTER);
            Bundle args1 = new Bundle();

            args1.putInt(TimelineFragment.EXTRA_MODE, TabType.TABTYPE_FILTER);
            args1.putSerializable(TimelineFragment.EXTRA_USER, user);
            args1.putSerializable(TimelineFragment.EXTRA_SHOW_USER, loadHolder.targetUser);
            args1.putString(TimelineFragment.EXTRA_TITLE, "Favorites: @" + loadHolder.targetUser.getScreenName());
            args1.putString(TimelineFragment.EXTRA_FILTER_QUERY, String.format("from favorite:\"%s/%s\"", user.ScreenName, loadHolder.targetUser.getScreenName()));

            fragment.setArguments(args1);

            FragmentTransaction transaction = getFragmentManager().beginTransaction();
            transaction.replace(R.id.frame, fragment, ProfileActivity.FRAGMENT_TAG_CONTENT);
            transaction.addToBackStack(null);
            transaction.commit();
        });
        pbFollows = v.findViewById(R.id.cvProfileFollows);
        pbFollows.setOnClickListener(view -> {
            Fragment fragment = TwitterUserListFragment.newFriendListInstance(user, "Follow: @" + loadHolder.targetUser.getScreenName(), loadHolder.targetUser);

            FragmentTransaction transaction = getFragmentManager().beginTransaction();
            transaction.replace(R.id.frame, fragment, ProfileActivity.FRAGMENT_TAG_CONTENT);
            transaction.addToBackStack(null);
            transaction.commit();
        });
        pbFollowers = v.findViewById(R.id.cvProfileFollowers);
        pbFollowers.setOnClickListener(view -> {
            Fragment fragment = TwitterUserListFragment.newFollowerListInstance(user, "Follower: @" + loadHolder.targetUser.getScreenName(), loadHolder.targetUser);

            FragmentTransaction transaction = getFragmentManager().beginTransaction();
            transaction.replace(R.id.frame, fragment, ProfileActivity.FRAGMENT_TAG_CONTENT);
            transaction.addToBackStack(null);
            transaction.commit();
        });

        btnFollowManage = (Button) v.findViewById(R.id.btnProfileFollow);
        btnFollowManage.setOnClickListener(view -> {
            FollowDialogFragment fragment = FollowDialogFragment.newInstance(new TwitterUser(loadHolder.targetUser), createKnownRelationshipList());
            fragment.setTargetFragment(ProfileFragment.this, 0);
            fragment.show(getFragmentManager(), "follow");
        });
        if (PreferenceManager.getDefaultSharedPreferences(getActivity()).getBoolean("allow_all_r4s", false)) {
            btnFollowManage.setOnLongClickListener(v1 -> {
                FollowDialogFragment fragment = FollowDialogFragment.newInstance(new TwitterUser(loadHolder.targetUser), createKnownRelationshipList(), true, true);
                fragment.setTargetFragment(ProfileFragment.this, 0);
                fragment.show(getFragmentManager(), "follow");
                return true;
            });
        }
        btnOwakareBlock = (Button) v.findViewById(R.id.btnBlock);
        btnOwakareBlock.setOnClickListener(v2 -> {
            FollowDialogFragment fragment = FollowDialogFragment.newInstance(new TwitterUser(loadHolder.targetUser), createKnownRelationshipList(), true, true);
            fragment.setTargetFragment(ProfileFragment.this, 0);
            fragment.show(getFragmentManager(), "follow");
        });

        appBarLayout = (AppBarLayout) v.findViewById(R.id.appBarLayout);
        appBarLayout.addOnOffsetChangedListener(new AppBarOffsetChangedCallback(ivProfileIcon));
        headerLayout = v.findViewById(R.id.headerLayout);

        return v;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (savedInstanceState != null) {
            loadHolder = savedInstanceState.getParcelable("loadHolder");
            user = (AuthUserRecord) savedInstanceState.getSerializable("user");
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

        ImageLoaderTask.loadProfileIcon(getActivity().getApplicationContext(), ivProfileIcon, holder.targetUser.getOriginalProfileImageURLHttps());
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

            // URLを展開する
            URLEntity[] urlEntities = holder.targetUser.getDescriptionURLEntities();
            if (urlEntities == null) {
                urlEntities = new URLEntity[0];
            }
            for (URLEntity entity : urlEntities) {
                bio = bio.replace(entity.getURL(), entity.getExpandedURL());
            }

            // Spannable化
            SpannableString spannableBio = new SpannableString(bio);

            // URLをリンク化
            for (URLEntity entity : urlEntities) {
                Pattern pattern = Pattern.compile(entity.getExpandedURL(), Pattern.LITERAL);
                Matcher matcher = pattern.matcher(spannableBio);

                while (matcher.find()) {
                    spannableBio.setSpan(new URLSpan(entity.getExpandedURL()), matcher.start(), matcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }

            // ScreenNameをリンク化
            Pattern screenNamePattern = Pattern.compile("@([a-zA-Z0-9_]{1,15})");
            Matcher screenNameMatcher = screenNamePattern.matcher(spannableBio);
            while (screenNameMatcher.find()) {
                String screenName = screenNameMatcher.group(1);
                spannableBio.setSpan(new UserProfileSpan(user, TwitterUtil.getProfileUrl(screenName)), screenNameMatcher.start(), screenNameMatcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }

            // ハッシュタグをリンク化
            Pattern hashTagPattern = Pattern.compile("[#\\uFF03]([a-zA-Z0-9_\\u3041-\\u3094\\u3099-\\u309C\\u30A1-\\u30FA\\u3400-\\uD7FF\\uFF10-\\uFF19\\uFF20-\\uFF3A\\uFF41-\\uFF5A\\uFF66-\\uFF9E]+)");
            Matcher hashTagMatcher = hashTagPattern.matcher(spannableBio);
            while (hashTagMatcher.find()) {
                String tag = hashTagMatcher.group(1);
                spannableBio.setSpan(new HashTagSpan(tag), hashTagMatcher.start(), hashTagMatcher.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }

            // 表示
            tvBio.setText(spannableBio);
            tvBio.setMovementMethod(LinkMovementMethod.getInstance());
            Log.d("ProfileFragment", "Profile: " + tvBio.getText());
        }

        if (PreferenceManager.getDefaultSharedPreferences(getActivity()).getBoolean("j_owakare_block", false)) {
            String bio = holder.targetUser.getDescription();
            if (bio == null) {
                bio = "";
            }

            if (bio.contains("お別れ") && bio.contains("ブロック")) {
                btnOwakareBlock.setVisibility(View.VISIBLE);
            }
        }

        tvLocation.setText(altIfEmpty(holder.targetUser.getLocation(), "－"));
        tvWeb.setText(altIfEmpty(holder.targetUser.getURLEntity().getExpandedURL(), "－"));
        {
            String dateStr = sdf.format(holder.targetUser.getCreatedAt());
            int totalDay = (int) ((System.currentTimeMillis() - holder.targetUser.getCreatedAt().getTime()) / A_DAY);
            float tpd = (float)holder.targetUser.getStatusesCount() / totalDay;

            tvSince.setText(String.format("%s (%d日, %.2ftweet/day)", dateStr, totalDay, tpd));
        }
        tvUserId.setText("#" + holder.targetUser.getId());

        pbTweets.setCount(String.valueOf(holder.targetUser.getStatusesCount()));
        pbFavorites.setCount(String.valueOf(holder.targetUser.getFavouritesCount()));
        pbFollows.setCount(String.valueOf(holder.targetUser.getFriendsCount()));
        pbFollowers.setCount(String.valueOf(holder.targetUser.getFollowersCount()));

        ivUserColor.setBackgroundColor(getTargetUserColor());
        if (holder.targetUser.getProfileLinkColor() != null) {
            int backgroundColor = Color.parseColor("#" + holder.targetUser.getProfileLinkColor());
            appBarLayout.setBackgroundColor(backgroundColor);
            headerLayout.setBackgroundColor(backgroundColor);

            ((AppCompatActivity) getActivity()).getSupportActionBar().setBackgroundDrawable(new ColorDrawable(backgroundColor));

            int statusBackgroundColor = Color.rgb(
                    (int) (Color.red(backgroundColor) * 0.8),
                    (int) (Color.green(backgroundColor) * 0.8),
                    (int) (Color.blue(backgroundColor) * 0.8));
            getActivity().getWindow().setStatusBarColor(statusBackgroundColor);
        }

        updateMenuItems();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable("loadHolder", loadHolder);
        outState.putSerializable("user", user);
    }

    @Override
    public void onResume() {
        super.onResume();
        getActivity().setTitle("プロフィール");
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
        UserExtrasManager userExtrasManager = getUserExtrasManager();
        if (userExtrasManager != null) {
            String url = TwitterUtil.getUrlFromUserId(loadHolder.targetUser.getId());
            for (UserExtras userExtra : userExtrasManager.getUserExtras()) {
                if (url.equals(userExtra.getId())) {
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

                    Twitter twitter = getTwitterService().getTwitter(claim.getSourceAccount());
                    if (twitter == null) {
                        sb.append("サービス通信エラー");
                        continue;
                    }

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

        switch (requestCode) {
            case REQUEST_PRIORITY_ACCOUNT:
                if (resultCode == Activity.RESULT_OK) {
                    AuthUserRecord userRecord = (AuthUserRecord) data.getSerializableExtra(AccountChooserActivity.EXTRA_SELECTED_RECORD);
                    if (loadHolder != null && loadHolder.targetUser != null && userRecord != null) {
                        getUserExtrasManager().setPriority(TwitterUtil.getUrlFromUserId(loadHolder.targetUser.getId()), userRecord);
                        Toast.makeText(getActivity(), "優先アカウントを @" + userRecord.ScreenName + " に設定しました", Toast.LENGTH_SHORT).show();
                        updateMenuItems();

                        user = userRecord;
                    } else {
                        Toast.makeText(getActivity(), "予期せぬ理由で、優先アカウントの設定に失敗しました", Toast.LENGTH_SHORT).show();
                    }
                }
                break;
        }
    }

    @Override
    public void onServiceConnected() {
        if (user == null) {
            user = getTwitterService().getPrimaryUser();
        }
        if (!serviceTasks.isEmpty()) {
            Runnable task;
            while ((task = serviceTasks.poll()) != null) {
                task.run();
            }
        }
    }

    @Override
    public void onServiceDisconnected() {

    }

    @Override
    public void onColorPicked(int color, String tag) {
        UserExtrasManager userExtrasManager = getUserExtrasManager();
        if (userExtrasManager != null) {
            userExtrasManager.setColor(TwitterUtil.getUrlFromUserId(loadHolder.targetUser.getId()), color);
            showProfile(loadHolder);
        } else {
            //TODO: 遅延処理にすべきかなあ
            Toast.makeText(getActivity().getApplicationContext(), "カラーラベル設定に失敗しました", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateMenuItems() {
        Menu menu = toolbar.getMenu();
        if ((loadHolder != null && loadHolder.targetUser != null && loadHolder.targetUser.getId() == user.NumericId) ||
                (targetId >= 0 && targetId == user.NumericId) ||
                (selfLoadId && selfLoadName.equals(user.ScreenName))) {
            menu.findItem(R.id.action_edit_profile).setVisible(true);
            menu.findItem(R.id.action_block_list).setVisible(true);

            menu.findItem(R.id.action_set_priority).setVisible(false);
            menu.findItem(R.id.action_unset_priority).setVisible(false);
        } else {
            if (loadHolder != null && loadHolder.targetUser != null) {
                Runnable task = () -> {
                    List<UserExtras> userExtras = getUserExtrasManager().getUserExtras();
                    String url = TwitterUtil.getUrlFromUserId(loadHolder.targetUser.getId());
                    Optional<UserExtras> userExtra = userExtras.stream().filter(ue -> url.equals(ue.getId())).findFirst();
                    AuthUserRecord priorityAccount = userExtra.orElseGet(() -> new UserExtras(url)).getPriorityAccount();
                    if (priorityAccount != null) {
                        menu.findItem(R.id.action_set_priority).setVisible(true).setTitle("優先アカウントを設定 (現在: @" + priorityAccount.ScreenName + ")");
                        menu.findItem(R.id.action_unset_priority).setVisible(true);
                    } else {
                        menu.findItem(R.id.action_set_priority).setVisible(true).setTitle("優先アカウントを設定 (未設定)");
                        menu.findItem(R.id.action_unset_priority).setVisible(false);
                    }
                };
                if (isTwitterServiceBound()) {
                    task.run();
                } else {
                    serviceTasks.add(task);
                }
            }

            menu.findItem(R.id.action_edit_profile).setVisible(false);
            menu.findItem(R.id.action_block_list).setVisible(false);
        }
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        if (loadHolder == null || loadHolder.targetUser == null) {
            Toast.makeText(getContext(), "何か調子が悪いみたいです。画面を一度開き直してみてください。", Toast.LENGTH_SHORT).show();
            return true;
        }

        switch (item.getItemId()) {
            case R.id.action_search: {
                Intent intent = new Intent(getActivity(), MainActivity.class);
                intent.putExtra(MainActivity.EXTRA_SEARCH_WORD, "@" + loadHolder.targetUser.getScreenName());
                startActivity(intent);
                return true;
            }
            case R.id.action_send_mention: {
                Intent intent = new Intent(getActivity(), TweetActivity.class);
                intent.putExtra(TweetActivity.EXTRA_USER, user);
                intent.putExtra(TweetActivity.EXTRA_MODE, TweetActivity.MODE_REPLY);
                intent.putExtra(TweetActivity.EXTRA_TEXT, "@" + loadHolder.targetUser.getScreenName() + " ");
                startActivity(intent);
                return true;
            }
            case R.id.action_send_message: {
                Intent intent = new Intent(getActivity(), TweetActivity.class);
                intent.putExtra(TweetActivity.EXTRA_MODE, TweetActivity.MODE_DM);
                intent.putExtra(TweetActivity.EXTRA_IN_REPLY_TO, TwitterUtil.getUrlFromUserId(loadHolder.targetUser.getId()));
                intent.putExtra(TweetActivity.EXTRA_DM_TARGET_SN, loadHolder.targetUser.getScreenName());
                startActivity(intent);
                return true;
            }
            case R.id.action_browser: {
                Intent intent = new Intent(Intent.ACTION_VIEW,
                        Uri.parse("http://twitter.com/" + loadHolder.targetUser.getScreenName()));
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                return true;
            }
            case R.id.action_following_list: {
                TwitterListFragment fragment = TwitterListFragment.newFollowingListInstance(
                        user,
                        "Lists: @" + loadHolder.targetUser.getScreenName(),
                        loadHolder.targetUser.getId());
                FragmentTransaction transaction = getFragmentManager().beginTransaction();
                transaction.replace(R.id.frame, fragment, ProfileActivity.FRAGMENT_TAG_CONTENT);
                transaction.addToBackStack(null);
                transaction.commit();
                return true;
            }
            case R.id.action_membership_list: {
                TwitterListFragment fragment = TwitterListFragment.newMembershipListInstance(
                        user,
                        "Listed: @" + loadHolder.targetUser.getScreenName(),
                        loadHolder.targetUser.getId());
                FragmentTransaction transaction = getFragmentManager().beginTransaction();
                transaction.replace(R.id.frame, fragment, ProfileActivity.FRAGMENT_TAG_CONTENT);
                transaction.addToBackStack(null);
                transaction.commit();
                return true;
            }
            case R.id.action_manage_list: {
                ListRegisterDialogFragment dialogFragment =
                        ListRegisterDialogFragment.newInstance(loadHolder.targetUser);
                dialogFragment.setTargetFragment(ProfileFragment.this, 1);
                dialogFragment.show(getFragmentManager(), "list");
                return true;
            }
            case R.id.action_mute: {
                MuteMenuDialogFragment dialogFragment =
                        MuteMenuDialogFragment.newInstance(loadHolder.targetUser, ProfileFragment.this);
                dialogFragment.show(getFragmentManager(), "mute");
                return true;
            }
            case R.id.action_set_color_label: {
                ColorPickerDialogFragment dialogFragment =
                        ColorPickerDialogFragment.newInstance(
                                getTargetUserColor(), "colorLabel"
                        );
                dialogFragment.setTargetFragment(ProfileFragment.this, 2);
                dialogFragment.show(getFragmentManager(), "colorLabel");
                return true;
            }
            case R.id.action_edit_profile: {
                Intent intent = new Intent(getActivity(), ProfileEditActivity.class);
                intent.putExtra(ProfileEditActivity.EXTRA_USER, user);
                startActivity(intent);
                return true;
            }
            case R.id.action_block_list: {
                TwitterUserListFragment fragment = TwitterUserListFragment.newBlockingListInstance(
                        user,
                        "Blocking: @" + loadHolder.targetUser.getScreenName(),
                        loadHolder.targetUser);
                FragmentTransaction transaction = getFragmentManager().beginTransaction();
                transaction.replace(R.id.frame, fragment, ProfileActivity.FRAGMENT_TAG_CONTENT);
                transaction.addToBackStack(null);
                transaction.commit();
                return true;
            }
            case R.id.action_set_priority: {
                Intent intent = new Intent(getActivity(), AccountChooserActivity.class);
                intent.putExtra(AccountChooserActivity.EXTRA_FILTER_PROVIDER_API_TYPE, Provider.API_TWITTER);
                startActivityForResult(intent, REQUEST_PRIORITY_ACCOUNT);
                return true;
            }
            case R.id.action_unset_priority: {
                getUserExtrasManager().setPriority(TwitterUtil.getUrlFromUserId(loadHolder.targetUser.getId()), null);
                Toast.makeText(getActivity(), "優先アカウントを解除しました", Toast.LENGTH_SHORT).show();

                user = (AuthUserRecord) getArguments().getSerializable(EXTRA_USER);
                if (user == null) {
                    user = getTwitterService().getPrimaryUser();
                }

                updateMenuItems();
                return true;
            }
        }

        return false;
    }

    private String altIfEmpty(String text, String ifEmpty) {
        if (text == null || "".equals(text.trim())) {
            return ifEmpty;
        }
        return text;
    }

    private ArrayList<FollowDialogFragment.KnownRelationship> createKnownRelationshipList() {
        ArrayList<FollowDialogFragment.KnownRelationship> entryList = new ArrayList<>();

        for (AuthUserRecord userRecord : loadHolder.relationships.keySet()) {
            Relationship relationship = loadHolder.relationships.get(userRecord);
            entryList.add(new FollowDialogFragment.KnownRelationship(userRecord, relationship));
        }

        return entryList;
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

        @NonNull
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
                TwitterService service = getTwitterService();
                User user = null;
                Twitter twitter = service.getTwitter(getCurrentUserOrPrimary());
                if (twitter != null) {
                    if (selfLoadId) {
                        String name = selfLoadName;
                        if (name.startsWith("@")) {
                            name = name.substring(1);
                        }
                        user = twitter.showUser(name);
                    } else {
                        user = twitter.showUser(targetId);
                    }
                }

                if (user != null) {
                    CentralDatabase database = service.getDatabase();
                    if (database != null) {
                        database.updateRecord(new DBUser(user));
                    }
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
                Toast.makeText(getActivity(), String.format(Locale.US, "[%d:%d] %s", e.getStatusCode(), e.getErrorCode(), e.getErrorMessage()), Toast.LENGTH_LONG).show();
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

        private AuthUserRecord getCurrentUserOrPrimary() {
            AuthUserRecord user = ProfileFragment.this.user;
            if (user != null) {
                return user;
            }
            return getAccountManager().getPrimaryUser();
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
                                    : new ArrayList<>()
                            : new ArrayList<>();
            for (AuthUserRecord userRecord : users) {
                try {
                    Twitter twitter = getTwitterService().getTwitter(userRecord);
                    if (twitter != null) {
                        relationships.put(userRecord, twitter.showFriendship(userRecord.NumericId, loadHolder.targetUser.getId()));
                    }
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
            btnOwakareBlock.setEnabled(false);
            btnFollowManage.setText("読み込み中...");
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            progressBar.setVisibility(View.INVISIBLE);
            btnFollowManage.setEnabled(true);
            btnOwakareBlock.setEnabled(true);
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
                    .setItems(items, (dialog1, which) -> {
                        dismiss();
                        ProfileFragment fragment = (ProfileFragment) getTargetFragment();
                        if (fragment != null) {
                            fragment.onSelectedMuteOption(which+1);
                        }
                    })
                    .setNegativeButton("キャンセル", (dialog1, which) -> {
                    })
                    .create();
            return dialog;
        }
    }

    public static class AppBarOffsetChangedCallback implements AppBarLayout.OnOffsetChangedListener {
        private WeakReference<ImageView> ivProfileIcon;

        public AppBarOffsetChangedCallback(ImageView ivProfileIcon) {
            this.ivProfileIcon = new WeakReference<>(ivProfileIcon);
        }

        @Override
        public void onOffsetChanged(AppBarLayout appBarLayout, int verticalOffset) {
            ImageView ivProfileIcon = this.ivProfileIcon.get();
            if (ivProfileIcon == null) {
                return;
            }

            final int threshold = (int) (appBarLayout.getTotalScrollRange() * 0.8);
            final int absOffset = Math.abs(verticalOffset);
            if (threshold < absOffset) {
                final float overflowHeight = (float) appBarLayout.getTotalScrollRange() * 0.2f;
                final float progress = (float)(absOffset - threshold) / overflowHeight;
                final float scale = 1.0f - 0.5f * progress;
                final float translate = overflowHeight * 0.5f * progress;
                ivProfileIcon.animate()
                        .scaleX(scale)
                        .scaleY(scale)
                        .translationX(-translate)
                        .translationY(translate)
                        .setDuration(0)
                        .start();
            } else {
                ivProfileIcon.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .translationX(0.0f)
                        .translationY(0.0f)
                        .setDuration(0)
                        .start();
            }
        }
    }
}
