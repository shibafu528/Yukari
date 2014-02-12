package shibafu.yukari.fragment;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Parcelable;
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
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.loopj.android.image.SmartImageView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import shibafu.yukari.R;
import shibafu.yukari.activity.TweetActivity;
import shibafu.yukari.common.IconLoaderTask;
import shibafu.yukari.common.TabType;
import shibafu.yukari.database.DBUser;
import shibafu.yukari.service.TwitterService;
import shibafu.yukari.twitter.AuthUserRecord;
import twitter4j.Relationship;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.URLEntity;
import twitter4j.User;

/**
 * Created by Shibafu on 13/08/10.
 */
public class ProfileFragment extends Fragment implements FollowDialogFragment.FollowDialogCallback{

    // TODO:画面回転とかが加わるとたまに「ユーザー情報の取得に失敗」メッセージを出力して復帰に失敗するみたい

    public static final String EXTRA_USER = "user";
    public static final String EXTRA_TARGET = "target";

    private final static SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");

    private AuthUserRecord user;
    private long targetId;
    private LoadHolder loadHolder = null;

    private boolean selfLoadId = false;
    private String selfLoadName;

    private TwitterService service;
    private boolean serviceBound = false;

    private SmartImageView ivProfileIcon, ivHeader;
    private TextView tvName, tvScreenName, tvBio, tvLocation, tvWeb, tvSince, tvUserId;
    private Button btnFollowManage;
    private ImageButton ibMenu, ibSearch;

    private GridView gridCommands;
    private CommandAdapter commandAdapter;

    private AsyncTask<Void, Void, LoadHolder> profileLoadTask = null;

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

        ivProfileIcon = (SmartImageView)v.findViewById(R.id.ivProfileIcon);
        ivHeader = (SmartImageView) v.findViewById(R.id.ivProfileHeader);

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

        ibMenu = (ImageButton) v.findViewById(R.id.ibProfileMenu);
        ibMenu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                List<String> menuList = new ArrayList<String>();
                menuList.add("ツイートを送る");
                menuList.add("DMを送る");
                menuList.add("ブラウザで開く");
                //menuList.add("リストへ追加/削除");

                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                builder.setItems(menuList.toArray(new String[0]), new DialogInterface.OnClickListener() {
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
                        fragment = new TweetListFragment();
                        args.putInt(TweetListFragment.EXTRA_MODE, TabType.TABTYPE_USER);
                        args.putSerializable(TweetListFragment.EXTRA_USER, user);
                        args.putSerializable(TweetListFragment.EXTRA_SHOW_USER, loadHolder.targetUser);
                        args.putString(TweetListFragment.EXTRA_TITLE, "Tweets: @" + loadHolder.targetUser.getScreenName());
                        break;
                    }
                    case 1:
                    {
                        fragment = new TweetListFragment();
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

        getActivity().bindService(new Intent(getActivity(), TwitterService.class), connection, Context.BIND_AUTO_CREATE);

        List<Command> commands = new ArrayList<Command>();
        commands.add(new Command(R.drawable.ic_prof_tweets, "Tweets", "0"));
        commands.add(new Command(R.drawable.ic_prof_favorite, "Favorites", "0"));
        commands.add(new Command(R.drawable.ic_prof_follow, "Follows", "0"));
        commands.add(new Command(R.drawable.ic_prof_follower, "Followers", "0"));

        commandAdapter = new CommandAdapter(getActivity(), commands);
        gridCommands.setAdapter(commandAdapter);

        if (savedInstanceState != null) {
            LoadHolder holder = savedInstanceState.getParcelable("loadHolder");
            showProfile(holder);
        }
        else {
            if (loadHolder != null) {
                showProfile(loadHolder);
            }
            else {
                final AsyncTask<Void, Void, LoadHolder> task = new AsyncTask<Void, Void, LoadHolder>() {
                    @Override
                    protected LoadHolder doInBackground(Void... params) {
                        if (!serviceBound) {
                            try {
                                Thread.sleep(3000);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            if (!serviceBound) {
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
                                user = service.getTwitter().showUser(name);
                            } else {
                                user = service.getTwitter().showUser(targetId);
                            }
                            service.getDatabase().updateUser(new DBUser(user));

                            if (ProfileFragment.this.user == null) {
                                ProfileFragment.this.user = service.getPrimaryUser();
                            }
                            if (user != null) {
                                LinkedHashMap<AuthUserRecord, Relationship> relationships =
                                        new LinkedHashMap<AuthUserRecord, Relationship>();
                                for (AuthUserRecord userRecord : service.getUsers()) {
                                    relationships.put(userRecord,
                                            service.getTwitter().showFriendship(userRecord.NumericId, user.getId()));
                                }
                                return new LoadHolder(user, relationships);
                            } else return null;
                        } catch (TwitterException e) {
                            e.printStackTrace();
                        }
                        return null;
                    }

                    @Override
                    protected void onPostExecute(LoadHolder holder) {
                        if (currentProgress != null) {
                            currentProgress.dismiss();
                            currentProgress = null;
                        }
                        showProfile(holder);
                        profileLoadTask = null;
                    }
                };

                currentProgress = new LoadDialogFragment();
                currentProgress.setTargetFragment(ProfileFragment.this, 0);
                currentProgress.show(getFragmentManager(), "Loading");

                profileLoadTask = task;
                task.execute();
            }
        }
    }

    private void showProfile(LoadHolder holder) {
        if (holder == null) {
            Toast.makeText(getActivity(), "ユーザー情報の取得に失敗しました", Toast.LENGTH_SHORT).show();
            getActivity().finish();
            return;
        }
        this.loadHolder = holder;

        ivProfileIcon.setTag(holder.targetUser.getBiggerProfileImageURL());
        IconLoaderTask loaderTask = new IconLoaderTask(getActivity(), ivProfileIcon);
        loaderTask.executeIf(holder.targetUser.getBiggerProfileImageURL());

        ivHeader.setImageUrl(holder.targetUser.getProfileBannerMobileURL());
        Log.d("ProfileFragment", "header url: " + holder.targetUser.getProfileBannerMobileURL());
        tvName.setText(holder.targetUser.getName());
        tvScreenName.setText("@" + holder.targetUser.getScreenName());

        //Bioはエスケープや短縮の展開を行う
        {
            String bio = holder.targetUser.getDescription();

            //TODO: リンクがうまくはれてないことがしばしばあるよ

            //URLを展開する
            URLEntity[] urlEntities = holder.targetUser.getDescriptionURLEntities();
            for (URLEntity entity : urlEntities) {
                bio = bio.replace(entity.getURL(), entity.getExpandedURL());
            }

            //改行コードをBRタグにする
            bio = bio.replaceAll("\r\n", "<br>").replaceAll("\n", "<br>");

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
        tvSince.setText(sdf.format(holder.targetUser.getCreatedAt()));

        commandAdapter.getItem(0).strBottom = String.valueOf(holder.targetUser.getStatusesCount());
        commandAdapter.getItem(1).strBottom = String.valueOf(holder.targetUser.getFavouritesCount());
        commandAdapter.getItem(2).strBottom = String.valueOf(holder.targetUser.getFriendsCount());
        commandAdapter.getItem(3).strBottom = String.valueOf(holder.targetUser.getFollowersCount());
        commandAdapter.notifyDataSetChanged();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable("loadHolder", loadHolder);
    }

    @Override
    public void onResume() {
        super.onResume();

        if (currentDialog != null) {
            currentDialog.show();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (serviceBound) {
            getActivity().unbindService(connection);
            serviceBound = false;
        }
    }

    public void onCancelledLoading() {
        if (profileLoadTask != null) {
            profileLoadTask.cancel(true);
        }
        getActivity().finish();
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
                    while (!serviceBound) {
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }

                    Twitter twitter = service.getTwitter();
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
                                sb.append("ブロックしました");
                            } catch (TwitterException e) {
                                e.printStackTrace();
                                sb.append("ブロック失敗:").append(e.getStatusCode());
                            }
                            break;
                        case FollowDialogFragment.RELATION_UNBLOCK:
                            try {
                                twitter.destroyBlock(claim.getTargetUser());
                                sb.append("ブロック解除しました");
                            } catch (TwitterException e) {
                                e.printStackTrace();
                                sb.append("ブロック解除失敗:").append(e.getStatusCode());
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

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            TwitterService.TweetReceiverBinder binder = (TwitterService.TweetReceiverBinder) service;
            ProfileFragment.this.service = binder.getService();
            serviceBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
        }
    };

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
            relationships = new LinkedHashMap<AuthUserRecord, Relationship>(mapSize);
            AuthUserRecord userRecord;
            Relationship relationship;
            for (int i = 0; i < mapSize; ++i) {
                userRecord = (AuthUserRecord) in.readSerializable();
                relationship = (Relationship) in.readSerializable();
                relationships.put(userRecord, relationship);
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
            out.writeInt(relationships.size());
            //K,V,K,...の順番でマップ内要素を出力
            for (AuthUserRecord userRecord : relationships.keySet()) {
                out.writeSerializable(userRecord);
                out.writeSerializable(relationships.get(userRecord));
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
}
