package shibafu.yukari.fragment;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.loopj.android.image.SmartImageView;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import shibafu.yukari.R;
import shibafu.yukari.common.FontAsset;
import shibafu.yukari.common.TweetDraft;
import twitter4j.TwitterException;
import twitter4j.User;

/**
 * Created by Shibafu on 13/08/07.
 */
public class DraftDialogFragment extends DialogFragment {

    private DraftDialogEventListener listener;

    private ListView listView;
    private DraftAdapter adapter;
    private List<TweetDraft> drafts = null;
    private AlertDialog currentDialog;
    private Handler handler = new Handler();

    public interface DraftDialogEventListener {
        void onDraftSelected(TweetDraft selected);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        listener = (DraftDialogEventListener) getActivity();
        drafts = TweetDraft.loadDrafts(getActivity());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        getDialog().setTitle("保存された下書き");

        listView = new ListView(getActivity());
        return listView;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (drafts == null || drafts.size() < 1) {
            Toast.makeText(getActivity(), "下書きがありません", Toast.LENGTH_SHORT).show();
            dismiss();
            return;
        }

        adapter = new DraftAdapter();
        listView.setAdapter(adapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                //アニメーションのスケジュール
                final View v = view;
                view.setBackgroundColor(Color.parseColor("#B394E0"));
                Timer t = new Timer();
                t.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                v.setBackgroundColor((Integer)v.getTag());
                            }
                        });
                    }
                }, new Date(System.currentTimeMillis() + 100));

                ((DraftDialogEventListener)getActivity()).onDraftSelected(drafts.get(position));
                dismiss();
            }
        });

        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                //アニメーションのスケジュール
                final View v = view;
                view.setBackgroundColor(Color.parseColor("#B394E0"));
                Timer t = new Timer();
                t.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                v.setBackgroundColor((Integer)v.getTag());
                            }
                        });
                    }
                }, new Date(System.currentTimeMillis() + 100));

                final int pos = position;
                AlertDialog ad = new AlertDialog.Builder(getActivity())
                        .setTitle("確認")
                        .setMessage("\"" + drafts.get(position).text + "\"を削除しますか？")
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                                currentDialog = null;

                                drafts.remove(pos);
                                adapter.notifyDataSetChanged();
                                try {
                                    TweetDraft.saveDrafts(getActivity(), drafts);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }

                                if (drafts.size() < 1) {
                                    Toast.makeText(getActivity(), "下書きがありません", Toast.LENGTH_SHORT).show();
                                    dismiss();
                                }
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
                return false;
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();

        if (currentDialog != null) {
            currentDialog.show();
        }
    }

    private class DraftAdapter extends BaseAdapter {

        private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.JAPAN);

        @Override
        public int getCount() {
            return drafts.size();
        }

        @Override
        public Object getItem(int position) {
            return drafts.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null) {
                LayoutInflater inflater = (LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                v = inflater.inflate(R.layout.raw_tweet, null);
            }

            final TweetDraft d = drafts.get(position);
            if (d != null) {
                v.setBackgroundColor(Color.WHITE);
                v.setTag(Color.WHITE);
                TextView tvName = (TextView) v.findViewById(R.id.tweet_name);
                tvName.setText("@" + d.user.ScreenName);
                tvName.setTypeface(FontAsset.getInstance(getActivity()).getFont());
                TextView tvText = (TextView) v.findViewById(R.id.tweet_text);
                tvText.setTypeface(FontAsset.getInstance(getActivity()).getFont());
                tvText.setText(d.text);
                final SmartImageView ivIcon = (SmartImageView)v.findViewById(R.id.tweet_icon);
                ivIcon.setImageResource(R.drawable.ic_launcher);
                {
                    AsyncTask<Void, Void, User> task = new AsyncTask<Void, Void, User>() {
                        @Override
                        protected User doInBackground(Void... params) {
                            try {
                                return d.user.getUser(getActivity());
                            } catch (TwitterException e) {
                                e.printStackTrace();
                            } catch (NullPointerException e) {
                                e.printStackTrace();
                            }
                            return null;
                        }

                        @Override
                        protected void onPostExecute(User user) {
                            if (user != null) {
                                ivIcon.setImageUrl(user.getProfileImageURL());
                            }
                        }
                    };
                    task.execute();
                }
                TextView tvTimestamp = (TextView)v.findViewById(R.id.tweet_timestamp);
                String info = "";
                if (d.attachMedia != null && d.attachMedia.length > 0) {
                    info = "添付: " + d.attachMedia.length + "\n";
                }
                info = info + "保存日時: " + sdf.format(d.time);
                tvTimestamp.setText(info);
            }

            return v;
        }
    }
}
