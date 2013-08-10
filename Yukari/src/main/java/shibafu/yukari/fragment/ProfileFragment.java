package shibafu.yukari.fragment;

import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.loopj.android.image.SmartImageView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import shibafu.yukari.R;
import shibafu.yukari.service.TwitterService;
import shibafu.yukari.twitter.AuthUserRecord;
import twitter4j.TwitterException;
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

    private TwitterService service;
    private boolean serviceBound = false;

    private SmartImageView ivProfileIcon, ivHeader;
    private TextView tvName, tvScreenName, tvBio, tvLocation, tvWeb, tvSince, tvUserId;

    private GridView gridCommands;
    private CommandAdapter commandAdapter;

    private ProgressDialog currentProgress;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_profile, container, false);

        Bundle args = getArguments();
        user = (AuthUserRecord) args.getSerializable(EXTRA_USER);
        targetId = args.getLong(EXTRA_TARGET, -1);

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

        gridCommands = (GridView) v.findViewById(R.id.gvProfileCommands);

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

        final AsyncTask<Void, Void, User> task = new AsyncTask<Void, Void, User>() {
            @Override
            protected User doInBackground(Void... params) {
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
                    User user = service.getTwitter().showUser(targetId);
                    return user;
                } catch (TwitterException e) {
                    e.printStackTrace();
                }
                return null;
            }

            @Override
            protected void onPostExecute(User user) {
                if (currentProgress != null) {
                    currentProgress.dismiss();
                    currentProgress = null;
                }
                if (user != null) {
                    ivProfileIcon.setImageUrl(user.getBiggerProfileImageURL());
                    ivHeader.setImageUrl(user.getProfileBannerMobileURL());
                    Log.d("ProfileFragment", "header url: " + user.getProfileBannerMobileURL());
                    tvName.setText(user.getName());
                    tvScreenName.setText("@" + user.getScreenName());
                    tvBio.setText(user.getDescription());
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
        };

        currentProgress = new ProgressDialog(getActivity());
        currentProgress.setMessage("読み込み中...");
        currentProgress.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        currentProgress.setIndeterminate(true);
        currentProgress.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                task.cancel(true);
                getActivity().finish();
            }
        });
        currentProgress.show();
        task.execute();
    }

    @Override
    public void onResume() {
        super.onResume();

        if (currentProgress != null) {
            currentProgress.show();
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
}
