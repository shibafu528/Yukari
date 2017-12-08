package shibafu.yukari.common;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import shibafu.yukari.R;
import shibafu.yukari.database.UserExtras;
import shibafu.yukari.entity.LoadMarker;
import shibafu.yukari.entity.NotifyHistory;
import shibafu.yukari.entity.Status;
import shibafu.yukari.linkage.StatusLoader;
import shibafu.yukari.mastodon.entity.DonStatus;
import shibafu.yukari.twitter.AuthUserRecord;
import shibafu.yukari.twitter.entity.TwitterMessage;
import shibafu.yukari.twitter.entity.TwitterStatus;
import shibafu.yukari.view.DonStatusView;
import shibafu.yukari.view.HistoryView;
import shibafu.yukari.view.MessageView;
import shibafu.yukari.view.StatusView;
import shibafu.yukari.view.TweetView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Shibafu on 13/08/01.
 */
public class TweetAdapter extends BaseAdapter {
    private Context context;
    private List<Status> statuses;
    private List<AuthUserRecord> userRecords;
    private List<UserExtras> userExtras;
    private WeakReference<StatusLoader> statusLoader;
    private LayoutInflater inflater;
    private StatusView.OnTouchProfileImageIconListener onTouchProfileImageIconListener;
    private SharedPreferences preferences;

    private static final int VT_LOAD_MARKER = 0;
    private static final int VT_TWEET = 1;
    private static final int VT_MESSAGE = 2;
    private static final int VT_HISTORY = 3;
    private static final int VT_DON_STATUS = 4;
    private static final int VT_COUNT = 5;

    public TweetAdapter(Context context,
                        List<AuthUserRecord> userRecords,
                        List<UserExtras> userExtras,
                        List<Status> statuses) {
        this.context = context;
        this.statuses = statuses;
        this.userRecords = userRecords;
        if (userExtras == null) {
            this.userExtras = new ArrayList<>();
        } else {
            this.userExtras = userExtras;
        }

        preferences = PreferenceManager.getDefaultSharedPreferences(context);
        inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    public void setUserExtras(List<UserExtras> userExtras) {
        if (userExtras != null) {
            this.userExtras = userExtras;
        } else {
            this.userExtras.clear();
        }
        notifyDataSetChanged();
    }

    public void setStatusLoader(StatusLoader statusLoader) {
        this.statusLoader = new WeakReference<>(statusLoader);
    }

    public void setOnTouchProfileImageIconListener(StatusView.OnTouchProfileImageIconListener listener) {
        this.onTouchProfileImageIconListener = listener;
    }

    @Override
    public int getCount() {
        return statuses.size();
    }

    @Override
    public Status getItem(int position) {
        return statuses.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getItemViewType(int position) {
        Status item = getItem(position);
        if (item instanceof LoadMarker) {
            return VT_LOAD_MARKER;
        } else if (item instanceof TwitterMessage) {
            return VT_MESSAGE;
        } else if (item instanceof NotifyHistory) {
            return VT_HISTORY;
        } else if (item instanceof TwitterStatus) {
            return VT_TWEET;
        } else if (item instanceof DonStatus) {
            return VT_DON_STATUS;
        }
        throw new UnsupportedOperationException("Unsupported Timeline Object!! : " + item.getClass().getName());
    }

    @Override
    public int getViewTypeCount() {
        return VT_COUNT;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Status item = getItem(position);

        int vt = getItemViewType(position);
        switch (vt) {
            default: {
                StatusView statusView;
                if (convertView == null) {
                    boolean singleLine = preferences.getBoolean("pref_mode_singleline", false);
                    switch (vt) {
                        default:
                            convertView = statusView = new TweetView(context, singleLine);
                            break;
                        case VT_MESSAGE:
                            convertView = statusView = new MessageView(context, singleLine);
                            break;
                        case VT_HISTORY:
                            convertView = statusView = new HistoryView(context, singleLine);
                            break;
                        case VT_DON_STATUS:
                            convertView = statusView = new DonStatusView(context, singleLine);
                            break;
                    }
                } else {
                    statusView = (StatusView) convertView;
                }
                statusView.setMode(StatusView.Mode.DEFAULT);
                statusView.setUserRecords(userRecords);
                statusView.setUserExtras(userExtras);
                statusView.setOnTouchProfileImageIconListener(onTouchProfileImageIconListener);
                statusView.setStatus(item);
                break;
            }
            case VT_LOAD_MARKER:
                if (convertView == null) {
                    convertView = inflater.inflate(R.layout.row_loading, null);
                }

                LoadMarker loadMarker = (LoadMarker) item;

                View progressBar = convertView.findViewById(R.id.pbLoading);
                TextView textView = (TextView) convertView.findViewById(R.id.tvLoading);

                if (statusLoader != null && statusLoader.get() != null && statusLoader.get().isRequestWorking(loadMarker.getTaskKey())) {
                    progressBar.setVisibility(View.VISIBLE);
                    textView.setText("loading");
                } else {
                    progressBar.setVisibility(View.INVISIBLE);
                    textView.setText("more");
                }
                break;
        }

        return convertView;
    }
}
