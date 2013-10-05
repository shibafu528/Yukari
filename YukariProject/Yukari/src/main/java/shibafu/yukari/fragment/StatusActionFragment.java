package shibafu.yukari.fragment;

import android.R;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.text.ClipboardManager;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import shibafu.yukari.activity.StatusActivity;
import shibafu.yukari.service.TwitterService;
import shibafu.yukari.twitter.AuthUserRecord;
import twitter4j.Status;

/**
 * Created by Shibafu on 13/08/02.
 */
@SuppressWarnings("deprecation")
public class StatusActionFragment extends ListFragment implements AdapterView.OnItemClickListener {
    private static final String[] ITEMS = {
            "ブラウザで開く",
            "クリップボードにコピー",
            "ブックマークする",
            "マルチアカウントRT/Fav",
            "アプリごとミュート"
    };

    private Status status = null;
    private AuthUserRecord user = null;

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle b = getArguments();
        status = (Status) b.getSerializable(StatusActivity.EXTRA_STATUS);
        user = (AuthUserRecord) b.getSerializable(StatusActivity.EXTRA_USER);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        setListAdapter(new ArrayAdapter<String>(getActivity(), R.layout.simple_list_item_1, ITEMS));
        getListView().setOnItemClickListener(this);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        switch (position) {
            case 0:
                startActivity(Intent.createChooser(new Intent(Intent.ACTION_VIEW, Uri.parse(TwitterService.getTweetURL(status))), null));
                break;
            case 1:
            {
                ClipboardManager cb = (ClipboardManager)getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
                cb.setText(TwitterService.createSTOT(status));
                Toast.makeText(getActivity(), "クリップボードにコピーしました", Toast.LENGTH_LONG).show();
                break;
            }
        }
    }
}
