package shibafu.yukari.fragment;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.ListFragment;
import android.support.v4.view.PagerTabStrip;
import android.support.v4.view.ViewPager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import shibafu.yukari.R;
import shibafu.yukari.service.TwitterService;
import shibafu.yukari.service.TwitterServiceDelegate;
import twitter4j.ResponseList;
import twitter4j.SavedSearch;
import twitter4j.Trend;
import twitter4j.TwitterException;

/**
 * Created by shibafu on 14/02/13.
 */
public class SearchDialogFragment extends DialogFragment implements TwitterServiceDelegate {

    public interface SearchDialogCallback {
        void onSearchQuery(String searchQuery, boolean isSavedSearch, boolean useTracking);
    }

    private InputMethodManager imm;
    private EditText searchQuery;
    private SectionsPagerAdapter adapter;
    private ViewPager viewPager;
    private TwitterServiceDelegate serviceDelegate;

    @Override
    public TwitterService getTwitterService() {
        return serviceDelegate.getTwitterService();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        serviceDelegate = (TwitterServiceDelegate) activity;
        if (serviceDelegate != null) {
            Log.d("SearchDialog", "Attached Service Delegate");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        serviceDelegate = null;
        Log.d("SearchDialog", "Detached Service Delegate");
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);

        Dialog dialog = new Dialog(getActivity());

        dialog.getContext().setTheme(R.style.YukariDialogTheme);
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        dialog.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        dialog.getWindow().setBackgroundDrawableResource(R.drawable.dialog_full_holo_light);

        dialog.setContentView(R.layout.dialog_search);
        {
            DisplayMetrics metrics = getResources().getDisplayMetrics();
            WindowManager.LayoutParams lp = dialog.getWindow().getAttributes();
            lp.width = (int) (metrics.widthPixels * 0.9);
            dialog.getWindow().setAttributes(lp);
        }

        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialogInterface) {
                imm.showSoftInput(searchQuery, InputMethodManager.SHOW_IMPLICIT);
            }
        });

        return dialog;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflateView(inflater);
    }

    private View inflateView(LayoutInflater inflater) {
        View v = inflater.inflate(R.layout.dialog_search, null);

        searchQuery = (EditText) v.findViewById(R.id.editText);
        searchQuery.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View view, int i, KeyEvent keyEvent) {
                if (keyEvent.getAction() == KeyEvent.ACTION_DOWN &&
                        keyEvent.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                    imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                    sendQuery();
                }
                return false;
            }
        });

        ImageButton ibSearch = (ImageButton) v.findViewById(R.id.ibSearch);
        ibSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendQuery();
            }
        });
        ibSearch.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                if (searchQuery.getText().length() > 0) {
                    searchQuery.setText(String.format("\"%s\"", searchQuery.getText().toString()));
                    sendQuery();
                    return true;
                }
                else return false;
            }
        });

        adapter = new SectionsPagerAdapter(getChildFragmentManager());

        viewPager = (ViewPager) v.findViewById(R.id.pager);
        viewPager.setAdapter(adapter);
        viewPager.setCurrentItem(1);

        PagerTabStrip tabStrip = (PagerTabStrip) v.findViewById(R.id.pager_title_strip);
        tabStrip.setDrawFullUnderline(true);
        tabStrip.setTabIndicatorColorResource(R.color.key_color);

        return v;
    }

    private void sendQuery() {
        sendQuery(searchQuery.getText().toString());
    }

    public void sendQuery(String query) {
        if (query.length() < 1) {
            Toast.makeText(getActivity(), "検索ワードが空です", Toast.LENGTH_LONG).show();
        }
        else {
            ((SearchDialogCallback)getActivity()).onSearchQuery(query, false, false);
            dismiss();
        }
    }

    private class SectionsPagerAdapter extends FragmentPagerAdapter {

        public SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int i) {
            Fragment fragment;
            switch (i) {
                case 0:
                    fragment = new HistoryFragment();
                    break;
                case 1:
                    fragment = new TrendFragment();
                    break;
                case 2:
                    fragment = new SavedSearchFragment();
                    break;
                default:
                    return null;
            }
            fragment.setTargetFragment(SearchDialogFragment.this, 0);
            return fragment;
        }

        @Override
        public int getCount() {
            return 3;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case 0:
                    return "History";
                case 1:
                    return "Trend";
                case 2:
                    return "Saved";
            }
            return null;
        }
    }

    public static abstract class SearchChildFragment extends ListFragment {
        private SearchDialogFragment parent;
        private TwitterService service;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            if (getTargetFragment() != null && getTargetFragment() instanceof SearchDialogFragment) {
                parent = (SearchDialogFragment) getTargetFragment();
            }
            else if (getParentFragment() != null && getParentFragment() instanceof SearchDialogFragment) {
                parent = (SearchDialogFragment) getParentFragment();
            }
            service = parent.getTwitterService();
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            parent = null;
            service = null;
        }

        protected TwitterService getService() {
            return service;
        }

        protected TwitterService getServiceAwait() {
            while (parent == null || parent.getTwitterService() == null) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            return parent.getTwitterService();
        }

        protected SearchDialogFragment getParent() {
            return parent;
        }
    }

    public static class HistoryFragment extends SearchChildFragment {
        @Override
        public void onActivityCreated(Bundle savedInstanceState) {
            super.onActivityCreated(savedInstanceState);
            setListAdapter(new ArrayAdapter<String>(getActivity(), android.R.layout.simple_list_item_1, new String[]{""}));
        }
    }

    public static class TrendFragment extends SearchChildFragment {
        private ArrayList<String> list;

        @Override
        public void onActivityCreated(Bundle savedInstanceState) {
            super.onActivityCreated(savedInstanceState);

            if (savedInstanceState != null) {
                list = savedInstanceState.getStringArrayList("trends");
            }

            if (list == null) {
                AsyncTask<Void, Void, Trend[]> task = new AsyncTask<Void, Void, Trend[]>() {
                    @Override
                    protected Trend[] doInBackground(Void... voids) {
                        try {
                            return getService().getTwitter().getPlaceTrends(1118370).getTrends();
                        } catch (TwitterException e) {
                            e.printStackTrace();
                        }
                        return null;
                    }

                    @Override
                    protected void onPostExecute(Trend[] trends) {
                        if (trends != null) {
                            list = new ArrayList<String>();
                            for (Trend t : trends) {
                                list.add(t.getName());
                            }
                            setListAdapter(new ArrayAdapter<String>(getActivity(), android.R.layout.simple_list_item_1, list));
                        }
                    }
                };
                task.execute();
            }
            else {
                setListAdapter(new ArrayAdapter<String>(getActivity(), android.R.layout.simple_list_item_1, list));
            }
        }

        @Override
        public void onListItemClick(ListView l, View v, int position, long id) {
            super.onListItemClick(l, v, position, id);
            String query = (String) getListAdapter().getItem(position);
            getParent().sendQuery(query);
        }

        @Override
        public void onSaveInstanceState(Bundle outState) {
            super.onSaveInstanceState(outState);
            if (list != null) {
                outState.putStringArrayList("trends", list);
            }
        }
    }

    public static class SavedSearchFragment extends SearchChildFragment {
        private ArrayList<SavedSearch> savedSearches;

        @Override
        public void onActivityCreated(Bundle savedInstanceState) {
            super.onActivityCreated(savedInstanceState);

            if (savedInstanceState != null) {
                savedSearches = (ArrayList<SavedSearch>) savedInstanceState.getSerializable("saved");
            }

            if (savedSearches == null) {
                AsyncTask<Void, Void, ResponseList<SavedSearch>> task = new AsyncTask<Void, Void, ResponseList<SavedSearch>>() {
                    @Override
                    protected ResponseList<SavedSearch> doInBackground(Void... voids) {
                        try {
                            return getServiceAwait().getTwitter().getSavedSearches();
                        } catch (TwitterException e) {
                            e.printStackTrace();
                        }
                        return null;
                    }

                    @Override
                    protected void onPostExecute(ResponseList<SavedSearch> savedSearches) {
                        if (savedSearches != null) {
                            ArrayList<SavedSearch> ss = new ArrayList<SavedSearch>(savedSearches);
                            SavedSearchFragment.this.savedSearches = ss;
                            setListAdapter(new SavedSearchAdapter(getActivity(), ss));
                        }
                        else {
                            Toast.makeText(getActivity(), "保存した検索の取得中にエラーが発生しました", Toast.LENGTH_LONG).show();
                        }
                    }
                };
                task.execute();
            }
            else {
                setListAdapter(new SavedSearchAdapter(getActivity(), savedSearches));
            }
        }

        @Override
        public void onSaveInstanceState(Bundle outState) {
            super.onSaveInstanceState(outState);
            outState.putSerializable("saved", savedSearches);
        }

        private class SavedSearchAdapter extends ArrayAdapter<SavedSearch> {

            public SavedSearchAdapter(Context context, List<SavedSearch> objects) {
                super(context, 0, objects);
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    LayoutInflater inflater = (LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    convertView = inflater.inflate(android.R.layout.simple_list_item_1, null);
                }

                SavedSearch ss = getItem(position);
                if (ss != null) {
                    TextView tv = (TextView) convertView.findViewById(android.R.id.text1);
                    tv.setText(ss.getName());
                }
                return convertView;
            }
        }
    }
}
