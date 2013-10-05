package shibafu.yukari.fragment;

import android.R;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.text.ClipboardManager;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private List<ResolveInfo> plugins;
    private List<String> pluginNames = new ArrayList<String>();

    private Status status = null;
    private AuthUserRecord user = null;

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle b = getArguments();
        status = (Status) b.getSerializable(StatusActivity.EXTRA_STATUS);
        user = (AuthUserRecord) b.getSerializable(StatusActivity.EXTRA_USER);

        PackageManager pm = getActivity().getPackageManager();
        Intent query = new Intent("jp.r246.twicca.ACTION_SHOW_TWEET");
        query.addCategory(Intent.CATEGORY_DEFAULT);
        plugins = pm.queryIntentActivities(query, PackageManager.MATCH_DEFAULT_ONLY);
        Collections.sort(plugins, new ResolveInfo.DisplayNameComparator(pm));
        pluginNames.clear();
        for (ResolveInfo ri : plugins) {
            pluginNames.add(ri.activityInfo.loadLabel(pm).toString());
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        ArrayList<String> menu = new ArrayList<String>();
        menu.addAll(Arrays.asList(ITEMS));
        menu.addAll(pluginNames);

        setListAdapter(new ArrayAdapter<String>(getActivity(), R.layout.simple_list_item_1, menu));
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
            case 2: break;
            case 3: break;
            case 4: break;
            default:
            {
                Intent intent = createPluginIntent(position - 5);
                if (intent != null) {
                    startActivity(intent);
                }
                break;
            }
        }
    }

    private Intent createPluginIntent(int id) {
        ResolveInfo ri = plugins.get(id);
        if (ri != null) {
            Intent intent = new Intent("jp.r246.twicca.ACTION_SHOW_TWEET");
            intent.addCategory(Intent.CATEGORY_DEFAULT);
            intent.setPackage(ri.activityInfo.packageName);
            intent.setClassName(ri.activityInfo.packageName, ri.activityInfo.name);

            intent.putExtra(Intent.EXTRA_TEXT, status.getText());
            intent.putExtra("id", String.valueOf(status.getId()));
            intent.putExtra("created_at", String.valueOf(status.getCreatedAt().getTime()));

            Pattern VIA_PATTERN = Pattern.compile("<a .*>(.+)</a>");
            Matcher matcher = VIA_PATTERN.matcher(status.getSource());
            String via;
            if (matcher.find()) {
                via = matcher.group(1);
            }
            else {
                via = status.getSource();
            }
            intent.putExtra("source", via);
            if (status.getInReplyToStatusId() > -1) {
                intent.putExtra("in_reply_to_status_id", status.getInReplyToStatusId());
            }
            intent.putExtra("user_screen_name", status.getUser().getScreenName());
            intent.putExtra("user_name", status.getUser().getName());
            intent.putExtra("user_id", String.valueOf(status.getUser().getId()));
            intent.putExtra("user_profile_image_url", status.getUser().getProfileImageURL());
            intent.putExtra("user_profile_image_url_mini", status.getUser().getMiniProfileImageURL());
            intent.putExtra("user_profile_image_url_normal", status.getUser().getOriginalProfileImageURL());
            intent.putExtra("user_profile_image_url_bigger", status.getUser().getBiggerProfileImageURL());

            return intent;
        }
        return null;
    }
}
