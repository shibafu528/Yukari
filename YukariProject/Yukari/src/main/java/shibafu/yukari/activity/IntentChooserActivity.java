package shibafu.yukari.activity;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.ListFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.util.Collections;
import java.util.List;

import shibafu.yukari.af2015.R;
import shibafu.yukari.util.ThemeUtil;

/**
 * Created by shibafu on 14/06/27.
 */
public class IntentChooserActivity extends FragmentActivity{

    public static final String EXTRA_FILTER = "filter";
    public static final String EXTRA_SELECT = "select";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeUtil.setDialogTheme(this);
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_parent);
        if (savedInstanceState == null) {
            Intent filter = getIntent().getParcelableExtra(EXTRA_FILTER);
            InnerFragment fragment = new InnerFragment();
            Bundle args = new Bundle();
            args.putParcelable(EXTRA_FILTER, filter);
            fragment.setArguments(args);
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.frame, fragment)
                    .commit();
        }
    }

    public void onResult(Intent intent) {
        setResult(RESULT_OK, intent);
        finish();
    }

    public static class InnerFragment extends ListFragment {

        private List<ResolveInfo> resolveInfos;

        @Override
        public void onViewCreated(View view, Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);
            Bundle args = getArguments();
            Intent filter = args.getParcelable(EXTRA_FILTER);

            PackageManager pm = getActivity().getPackageManager();
            resolveInfos = pm.queryIntentActivities(filter, PackageManager.MATCH_DEFAULT_ONLY);
            Collections.sort(resolveInfos, new ResolveInfo.DisplayNameComparator(pm));
            setListAdapter(new InfoAdapter(getActivity(), resolveInfos));
        }

        @Override
        public void onListItemClick(ListView l, View v, int position, long id) {
            super.onListItemClick(l, v, position, id);
            Bundle args = getArguments();
            Intent filter = args.getParcelable(EXTRA_FILTER);
            Intent result = new Intent();
            result.putExtra(EXTRA_FILTER, filter);
            result.putExtra(EXTRA_SELECT, resolveInfos.get(position));
            ((IntentChooserActivity)getActivity()).onResult(result);
        }

        private class InfoAdapter extends ArrayAdapter<ResolveInfo> {
            class ViewHolder {
                ImageView imageView;
                TextView textView;

                ViewHolder(View v) {
                    this.imageView = (ImageView) v.findViewById(R.id.imageView);
                    this.textView = (TextView) v.findViewById(R.id.textView);
                }
            }

            private LayoutInflater inflater;
            private PackageManager pm;

            public InfoAdapter(Context context, List<ResolveInfo> objects) {
                super(context, 0, objects);
                inflater = (LayoutInflater) context.getSystemService(LAYOUT_INFLATER_SERVICE);
                pm = context.getPackageManager();
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                ViewHolder vh;
                if (convertView == null) {
                    convertView = inflater.inflate(R.layout.row_intent, null);
                    vh = new ViewHolder(convertView);
                    convertView.setTag(vh);
                } else {
                    vh = (ViewHolder) convertView.getTag();
                }
                ResolveInfo info = getItem(position);
                vh.textView.setText(info.loadLabel(pm));
                vh.imageView.setImageDrawable(info.loadIcon(pm));
                return convertView;
            }
        }
    }
}
