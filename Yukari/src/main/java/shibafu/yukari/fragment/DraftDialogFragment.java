package shibafu.yukari.fragment;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import shibafu.yukari.R;
import shibafu.yukari.common.FontAsset;
import shibafu.yukari.common.bitmapcache.ImageLoaderTask;
import shibafu.yukari.database.DBUser;
import shibafu.yukari.entity.StatusDraft;
import shibafu.yukari.service.TwitterService;
import shibafu.yukari.service.TwitterServiceDelegate;
import shibafu.yukari.twitter.AuthUserRecord;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

/**
 * Created by Shibafu on 13/08/07.
 */
public class DraftDialogFragment extends DialogFragment {

    private DraftDialogEventListener listener;
    private TwitterService service;

    private ListView listView;
    private DraftAdapter adapter;
    private List<StatusDraft> drafts = null;
    private AlertDialog currentDialog;

    public interface DraftDialogEventListener {
        void onDraftSelected(StatusDraft selected);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        listener = (DraftDialogEventListener) getActivity();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        service = ((TwitterServiceDelegate)context).getTwitterService();
        drafts = service.getDatabase().getDrafts();
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

        listView.setOnItemClickListener((parent, view, position, id) -> {
            listener.onDraftSelected(drafts.get(position));
            dismiss();
        });

        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            final int pos = position;
            AlertDialog ad = new AlertDialog.Builder(getActivity())
                    .setTitle("確認")
                    .setMessage("\"" + drafts.get(position).getText() + "\"を削除しますか？")
                    .setPositiveButton("OK", (dialog, which) -> {
                        dialog.dismiss();
                        currentDialog = null;

                        service.getDatabase().deleteDraft(drafts.get(pos));
                        drafts = service.getDatabase().getDrafts();
                        adapter.notifyDataSetChanged();

                        if (drafts.size() < 1) {
                            Toast.makeText(getActivity(), "下書きがありません", Toast.LENGTH_SHORT).show();
                            dismiss();
                        }
                    })
                    .setNegativeButton("キャンセル", (dialog, which) -> {
                        dialog.dismiss();
                        currentDialog = null;
                    })
                    .setOnCancelListener(dialog -> {
                        dialog.dismiss();
                        currentDialog = null;
                    })
                    .create();
            ad.show();
            currentDialog = ad;
            return true;
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
                v = inflater.inflate(R.layout.row_tweet, null);
            }

            final StatusDraft d = drafts.get(position);
            if (d != null) {
                TextView tvName = (TextView) v.findViewById(R.id.tweet_name);
                StringBuilder sbNames = new StringBuilder();
                for (int i = 0; i < d.getWriters().size(); ++i) {
                    if (i > 0) sbNames.append("\n");
                    sbNames.append("@");
                    sbNames.append(d.getWriters().get(i).ScreenName);
                }
                tvName.setText(sbNames.toString());
                tvName.setTypeface(FontAsset.getInstance(getActivity()).getFont());

                TextView tvText = (TextView) v.findViewById(R.id.tweet_text);
                tvText.setTypeface(FontAsset.getInstance(getActivity()).getFont());
                tvText.setText(d.getText());

                final ImageView ivIcon = (ImageView)v.findViewById(R.id.tweet_icon);
                AuthUserRecord user = d.getWriters().get(0);
                ImageLoaderTask.loadProfileIcon(getActivity().getApplicationContext(), ivIcon, user.ProfileImageUrl);

                TextView tvTimestamp = (TextView)v.findViewById(R.id.tweet_timestamp);
                String info = "";
                if (d.isDirectMessage()) {
                    DBUser dbUser = service.getDatabase().getUser(d.getInReplyTo());
                    info += "DM to " + (dbUser!=null? "@" + dbUser.getScreenName() : "(Unknown User)") + "\n";
                }
                if (d.isFailedDelivery()) {
                    info += "送信に失敗したツイート\n";
                }
                if (!d.getAttachPictures().isEmpty()) {
                    info += "添付画像あり\n";
                }
                if (d.getUseGeoLocation()) {
                    info += "座標情報あり(" + d.getGeoLatitude() + ", " + d.getGeoLongitude() + ")\n";
                }
                info += "保存日時: " + sdf.format(d.getDateTime());
                tvTimestamp.setText(info);
            }

            return v;
        }
    }
}
