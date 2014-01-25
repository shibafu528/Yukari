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
import twitter4j.TwitterException;
import twitter4j.URLEntity;
import twitter4j.User;

/**
 * Created by Shibafu on 13/08/10.
 */
public class ProfileFragment extends Fragment{

    public static final String EXTRA_USER = "user";
    public static final String EXTRA_TARGET = "target";

    private final static SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");

    private AuthUserRecord user;
    private long targetId;
    private User targetUser = null;
    private Relationship relation = null;

    private boolean selfLoadId = false;
    private String selfLoadName;

    private TwitterService service;
    private boolean serviceBound = false;

    private SmartImageView ivProfileIcon, ivHeader, ivProfileCurrent;
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
        ivProfileCurrent = (SmartImageView) v.findViewById(R.id.ivProfileCurrent);
        AsyncTask<Void, Void, String> task = new AsyncTask<Void, Void, String>() {
            @Override
            protected String doInBackground(Void... params) {
                if (user == null) return null;
                else return user.ProfileImageUrl;
            }

            @Override
            protected void onPostExecute(String s) {
                if (s != null) {
                    ivProfileCurrent.setTag(s);
                    IconLoaderTask loaderTask = new IconLoaderTask(getActivity(), ivProfileCurrent);
                    loaderTask.executeIf(s);
                }
            }
        };
        task.execute();

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
                args.putSerializable(FollowDialogFragment.ARGUMENT_TARGET, targetUser);
                fragment.setArguments(args);
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
                                intent.putExtra(TweetActivity.EXTRA_TEXT, "@" + targetUser.getScreenName() + " ");
                                startActivity(intent);
                                break;
                            }
                            case 1:
                            {
                                Intent intent = new Intent(getActivity(), TweetActivity.class);
                                intent.putExtra(TweetActivity.EXTRA_MODE, TweetActivity.MODE_DM);
                                intent.putExtra(TweetActivity.EXTRA_IN_REPLY_TO, targetUser.getId());
                                intent.putExtra(TweetActivity.EXTRA_DM_TARGET_SN, targetUser.getScreenName());
                                startActivity(intent);
                                break;
                            }
                            case 2:
                            {
                                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://twitter.com/" + targetUser.getScreenName()));
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
                        args.putSerializable(TweetListFragment.EXTRA_SHOW_USER, targetUser);
                        args.putString(TweetListFragment.EXTRA_TITLE, "Tweets: @" + targetUser.getScreenName());
                        break;
                    }
                    case 1:
                    {
                        fragment = new TweetListFragment();
                        args.putInt(TweetListFragment.EXTRA_MODE, TabType.TABTYPE_FAVORITE);
                        args.putSerializable(TweetListFragment.EXTRA_USER, user);
                        args.putSerializable(TweetListFragment.EXTRA_SHOW_USER, targetUser);
                        args.putString(TweetListFragment.EXTRA_TITLE, "Favorites: @" + targetUser.getScreenName());
                        break;
                    }
                    case 2:
                    {
                        fragment = new FriendListFragment();
                        args.putInt(FriendListFragment.EXTRA_MODE, FriendListFragment.MODE_FRIEND);
                        args.putSerializable(TweetListFragment.EXTRA_USER, user);
                        args.putSerializable(TweetListFragment.EXTRA_SHOW_USER, targetUser);
                        args.putString(TweetListFragment.EXTRA_TITLE, "Follow: @" + targetUser.getScreenName());
                        break;
                    }
                    case 3:
                    {
                        fragment = new FriendListFragment();
                        args.putInt(FriendListFragment.EXTRA_MODE, FriendListFragment.MODE_FOLLOWER);
                        args.putSerializable(TweetListFragment.EXTRA_USER, user);
                        args.putSerializable(TweetListFragment.EXTRA_SHOW_USER, targetUser);
                        args.putString(TweetListFragment.EXTRA_TITLE, "Follower: @" + targetUser.getScreenName());
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
            targetUser = (User) savedInstanceState.getSerializable(EXTRA_TARGET);
            relation = (Relationship) savedInstanceState.getSerializable("relation");
            showProfile(new LoadHolder(targetUser, relation));
        }
        else if (targetUser != null && relation != null) {
            showProfile(new LoadHolder(targetUser, relation));
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
                        }
                        else {
                            user = service.getTwitter().showUser(targetId);
                        }
                        targetUser = user;
                        service.getDatabase().updateUser(new DBUser(user));

                        if (ProfileFragment.this.user == null) {
                            ProfileFragment.this.user = service.getPrimaryUser();
                        }
                        Relationship relationship = null;
                        if (user != null)
                            relationship = service.getTwitter().showFriendship(ProfileFragment.this.user.NumericId, user.getId());
                        return new LoadHolder(user, relationship);
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
            currentProgress.show(getFragmentManager(), "Loading");

            profileLoadTask = task;
            task.execute();
        }
    }

    private void showProfile(LoadHolder holder) {
        if (holder == null) {
            Toast.makeText(getActivity(), "ユーザー情報の取得に失敗しました", Toast.LENGTH_SHORT).show();
            getActivity().finish();
            return;
        }
        User user = holder.user;
        Relationship relationship = holder.relationship;
        relation = relationship;
        if (user != null) {
            ivProfileIcon.setTag(user.getBiggerProfileImageURL());
            IconLoaderTask loaderTask = new IconLoaderTask(getActivity(), ivProfileIcon);
            loaderTask.executeIf(user.getBiggerProfileImageURL());

            ivHeader.setImageUrl(user.getProfileBannerMobileURL());
            Log.d("ProfileFragment", "header url: " + user.getProfileBannerMobileURL());
            tvName.setText(user.getName());
            tvScreenName.setText("@" + user.getScreenName());

            //Bioはエスケープや短縮の展開を行う
            {
                String bio = user.getDescription();

                //TODO: リンクがうまくはれてないことがしばしばあるよ

                //URLを展開する
                URLEntity[] urlEntities = user.getDescriptionURLEntities();
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

            tvLocation.setText(user.getLocation());
            tvWeb.setText(user.getURLEntity().getExpandedURL());
            tvSince.setText(sdf.format(user.getCreatedAt()));

            commandAdapter.getItem(0).strBottom = String.valueOf(user.getStatusesCount());
            commandAdapter.getItem(1).strBottom = String.valueOf(user.getFavouritesCount());
            commandAdapter.getItem(2).strBottom = String.valueOf(user.getFriendsCount());
            commandAdapter.getItem(3).strBottom = String.valueOf(user.getFollowersCount());
            commandAdapter.notifyDataSetChanged();
        }
        else {
            Toast.makeText(getActivity(), "ユーザー情報の取得に失敗しました", Toast.LENGTH_SHORT).show();
            getActivity().finish();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(EXTRA_TARGET, targetUser);
        outState.putSerializable("relation", relation);
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

    private class LoadHolder {
        User user;
        Relationship relationship;

        private LoadHolder(User user, Relationship relationship) {
            this.user = user;
            this.relationship = relationship;
        }
    }

    private class LoadDialogFragment extends DialogFragment {
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
            profileLoadTask.cancel(true);
            getActivity().finish();
        }
    }
}
