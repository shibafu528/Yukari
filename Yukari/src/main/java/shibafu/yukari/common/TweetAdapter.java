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
import shibafu.yukari.twitter.AuthUserRecord;
import shibafu.yukari.twitter.statusimpl.HistoryStatus;
import shibafu.yukari.twitter.statusimpl.LoadMarkerStatus;
import shibafu.yukari.twitter.statusimpl.PreformedStatus;
import shibafu.yukari.twitter.statusmanager.StatusManager;
import shibafu.yukari.view.HistoryView;
import shibafu.yukari.view.MessageView;
import shibafu.yukari.view.StatusView;
import shibafu.yukari.view.TweetView;
import twitter4j.DirectMessage;
import twitter4j.Status;
import twitter4j.TwitterResponse;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Shibafu on 13/08/01.
 */
public class TweetAdapter extends BaseAdapter {
    private Context context;
    private List<? extends TwitterResponse> statuses;
    private List<AuthUserRecord> userRecords;
    private List<UserExtras> userExtras;
    private WeakReference<StatusManager> statusManager;
    private LayoutInflater inflater;
    private StatusView.OnTouchProfileImageIconListener onTouchProfileImageIconListener;
    private SharedPreferences preferences;

    private int clsType;
    private static final int CLS_STATUS = 0;
    private static final int CLS_DM = 1;
    private static final int CLS_UNKNOWN = 2;

    private static final int VT_LOAD_MARKER = 0;
    private static final int VT_TWEET = 1;
    private static final int VT_MESSAGE = 2;
    private static final int VT_HISTORY = 3;
    private static final int VT_COUNT = 4;

    public TweetAdapter(Context context,
                        List<AuthUserRecord> userRecords,
                        List<UserExtras> userExtras,
                        List<? extends TwitterResponse> statuses,
                        Class<? extends TwitterResponse> clz) {
        this.context = context;
        this.statuses = statuses;
        this.userRecords = userRecords;
        if (userExtras == null) {
            this.userExtras = new ArrayList<>();
        } else {
            this.userExtras = userExtras;
        }

        if (Status.class.isAssignableFrom(clz)) {
            clsType = CLS_STATUS;
        } else if (DirectMessage.class.isAssignableFrom(clz)) {
            clsType = CLS_DM;
        } else {
            clsType = CLS_UNKNOWN;
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

    public void setStatusManager(StatusManager statusManager) {
        this.statusManager = new WeakReference<>(statusManager);
    }

    public void setOnTouchProfileImageIconListener(StatusView.OnTouchProfileImageIconListener listener) {
        this.onTouchProfileImageIconListener = listener;
    }

    @Override
    public int getCount() {
        return statuses.size();
    }

    @Override
    public TwitterResponse getItem(int position) {
        return statuses.get(position);
    }

    @Override
    public long getItemId(int position) {
        switch (clsType) {
            case CLS_STATUS:
                return ((Status)statuses.get(position)).getId();
            case CLS_DM:
                return ((DirectMessage)statuses.get(position)).getId();
            default:
                return position;
        }
    }

    @Override
    public int getItemViewType(int position) {
        TwitterResponse item = getItem(position);
        if (item instanceof PreformedStatus && ((PreformedStatus) item).getBaseStatusClass() == LoadMarkerStatus.class) {
            return VT_LOAD_MARKER;
        } else if (item instanceof DirectMessage) {
            return VT_MESSAGE;
        } else if (item instanceof HistoryStatus) {
            return VT_HISTORY;
        }
        return VT_TWEET;
    }

    @Override
    public int getViewTypeCount() {
        return VT_COUNT;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        TwitterResponse item = getItem(position);

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

                LoadMarkerStatus loadMarker = (LoadMarkerStatus) ((PreformedStatus) item).getBaseStatus();

                View progressBar = convertView.findViewById(R.id.pbLoading);
                TextView textView = (TextView) convertView.findViewById(R.id.tvLoading);

                if (statusManager != null && statusManager.get() != null && statusManager.get().isWorkingRestQuery(loadMarker.getTaskKey())) {
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
