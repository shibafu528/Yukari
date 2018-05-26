package shibafu.yukari.fragment;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.ListFragment;
import android.support.v4.view.PagerTabStrip;
import android.support.v4.view.ViewPager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import shibafu.yukari.R;
import shibafu.yukari.common.async.ParallelAsyncTask;
import shibafu.yukari.common.async.ThrowableAsyncTask;
import shibafu.yukari.common.async.TwitterAsyncTask;
import shibafu.yukari.database.SearchHistory;
import shibafu.yukari.service.TwitterService;
import shibafu.yukari.service.TwitterServiceDelegate;
import shibafu.yukari.util.AttrUtil;
import twitter4j.ResponseList;
import twitter4j.SavedSearch;
import twitter4j.Trend;
import twitter4j.Twitter;
import twitter4j.TwitterException;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by shibafu on 14/02/13.
 */
public class SearchDialogFragment extends DialogFragment implements TwitterServiceDelegate {

    public interface SearchDialogCallback {
        void onSearchQuery(String searchQuery, boolean isSavedSearch, boolean useTracking);
    }

    private View spacer;
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
    public boolean isTwitterServiceBound() {
        return serviceDelegate.isTwitterServiceBound();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        serviceDelegate = (TwitterServiceDelegate) context;
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

        switch (PreferenceManager.getDefaultSharedPreferences(getActivity()).getString("pref_theme", "light")) {
            case "light":
                dialog.getContext().setTheme(R.style.ColorsTheme_Light_Dialog);
                dialog.getWindow().setBackgroundDrawableResource(R.drawable.dialog_full_material_light);
                break;
            case "dark":
                dialog.getContext().setTheme(R.style.ColorsTheme_Dark_Dialog);
                dialog.getWindow().setBackgroundDrawableResource(R.drawable.dialog_full_material_dark);
                break;
            case "akari":
                dialog.getContext().setTheme(R.style.ColorsTheme_Akari_Dialog);
                dialog.getWindow().setBackgroundDrawableResource(R.drawable.dialog_full_material_light);
                break;
            case "akari_dark":
                dialog.getContext().setTheme(R.style.ColorsTheme_Akari_Dark_Dialog);
                dialog.getWindow().setBackgroundDrawableResource(R.drawable.dialog_full_material_dark);
                break;
            case "zunko":
                dialog.getContext().setTheme(R.style.ColorsTheme_Zunko_Dialog);
                dialog.getWindow().setBackgroundDrawableResource(R.drawable.dialog_full_material_light);
                break;
            case "zunko_dark":
                dialog.getContext().setTheme(R.style.ColorsTheme_Zunko_Dark_Dialog);
                dialog.getWindow().setBackgroundDrawableResource(R.drawable.dialog_full_material_dark);
                break;
            case "maki":
                dialog.getContext().setTheme(R.style.ColorsTheme_Maki_Dialog);
                dialog.getWindow().setBackgroundDrawableResource(R.drawable.dialog_full_material_light);
                break;
            case "maki_dark":
                dialog.getContext().setTheme(R.style.ColorsTheme_Maki_Dark_Dialog);
                dialog.getWindow().setBackgroundDrawableResource(R.drawable.dialog_full_material_dark);
                break;
            case "aoi":
                dialog.getContext().setTheme(R.style.ColorsTheme_Aoi_Dialog);
                dialog.getWindow().setBackgroundDrawableResource(R.drawable.dialog_full_material_light);
                break;
            case "aoi_dark":
                dialog.getContext().setTheme(R.style.ColorsTheme_Aoi_Dark_Dialog);
                dialog.getWindow().setBackgroundDrawableResource(R.drawable.dialog_full_material_dark);
                break;
            case "akane":
                dialog.getContext().setTheme(R.style.ColorsTheme_Akane_Dialog);
                dialog.getWindow().setBackgroundDrawableResource(R.drawable.dialog_full_material_light);
                break;
            case "akane_dark":
                dialog.getContext().setTheme(R.style.ColorsTheme_Akane_Dark_Dialog);
                dialog.getWindow().setBackgroundDrawableResource(R.drawable.dialog_full_material_dark);
                break;
        }
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        dialog.getWindow().requestFeature(Window.FEATURE_NO_TITLE);

        dialog.setContentView(R.layout.dialog_search);
        {
            DisplayMetrics metrics = getResources().getDisplayMetrics();
            WindowManager.LayoutParams lp = dialog.getWindow().getAttributes();
            lp.width = (int) (metrics.widthPixels * 0.9);
            dialog.getWindow().setAttributes(lp);
        }

        dialog.setOnShowListener(dialogInterface -> imm.showSoftInput(searchQuery, InputMethodManager.SHOW_IMPLICIT));

        return dialog;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflateView(inflater);
    }

    private View inflateView(LayoutInflater inflater) {
        View v = inflater.inflate(R.layout.dialog_search, null);

        spacer = v.findViewById(R.id.spacer);

        searchQuery = (EditText) v.findViewById(R.id.editText);
        searchQuery.setOnKeyListener((view, i, keyEvent) -> {
            if (keyEvent.getAction() == KeyEvent.ACTION_DOWN &&
                    keyEvent.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                sendQuery();
            }
            return false;
        });
        searchQuery.setCustomSelectionActionModeCallback(new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode actionMode, Menu menu) {
                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode actionMode, Menu menu) {
                spacer.setVisibility(View.VISIBLE);
                return true;
            }

            @Override
            public boolean onActionItemClicked(ActionMode actionMode, MenuItem menuItem) {
                return false;
            }

            @Override
            public void onDestroyActionMode(ActionMode actionMode) {
                spacer.setVisibility(View.GONE);
            }
        });

        ImageButton ibSearch = (ImageButton) v.findViewById(R.id.ibSearch);
        ibSearch.setOnClickListener(view -> sendQuery());
        ibSearch.setOnLongClickListener(view -> {
            if (searchQuery.getText().length() > 0) {
                searchQuery.setText(String.format("\"%s\"", searchQuery.getText().toString()));
                sendQuery();
                return true;
            }
            else return false;
        });

        adapter = new SectionsPagerAdapter(getChildFragmentManager());

        viewPager = (ViewPager) v.findViewById(R.id.pager);
        viewPager.setAdapter(adapter);
        viewPager.setCurrentItem(1);

        PagerTabStrip tabStrip = (PagerTabStrip) v.findViewById(R.id.pager_title_strip);
        tabStrip.setDrawFullUnderline(true);
        tabStrip.setTabIndicatorColorResource(AttrUtil.resolveAttribute(getActivity().getTheme(), R.attr.colorPrimary));

        return v;
    }

    private void sendQuery() {
        sendQuery(searchQuery.getText().toString(), false);
    }

    public void sendQuery(String query, boolean isSavedSearch) {
        if (query.length() < 1) {
            Toast.makeText(getActivity(), "検索ワードが空です", Toast.LENGTH_LONG).show();
        }
        else {
            //DBに検索履歴を保存
            getTwitterService().getDatabase().updateSearchHistory(query);
            //コールバック着火
            ((SearchDialogCallback)getActivity()).onSearchQuery(query, isSavedSearch, false);
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

    public static class HistoryFragment extends SearchChildFragment
            implements AdapterView.OnItemLongClickListener, DialogInterface.OnClickListener {
        private List<SearchHistory> searchHistories;
        private AsyncTask<Void, Void, List<SearchHistory>> task;
        private SearchHistory selected;

        @Override
        public void onActivityCreated(Bundle savedInstanceState) {
            super.onActivityCreated(savedInstanceState);

            reloadHistory();

            getListView().setOnItemLongClickListener(this);
        }

        private void reloadHistory() {
            task = new ParallelAsyncTask<Void, Void, List<SearchHistory>>() {
                @Override
                protected List<SearchHistory> doInBackground(Void... params) {
                    return getServiceAwait().getDatabase().getSearchHistories();
                }

                @Override
                protected void onPostExecute(List<SearchHistory> searchHistories) {
                    task = null;
                    if (isCancelled()) return;
                    HistoryFragment.this.searchHistories = searchHistories;
                    setListAdapter(new ArrayAdapter<>(getActivity(), android.R.layout.simple_list_item_1, searchHistories));
                }
            };
            task.execute();
        }

        @Override
        public void onListItemClick(ListView l, View v, int position, long id) {
            super.onListItemClick(l, v, position, id);
            String query = searchHistories.get(position).getQuery();
            getParent().sendQuery(query, false);
        }

        @Override
        public void onDestroyView() {
            super.onDestroyView();
            if (task != null) {
                task.cancel(true);
            }
        }

        @Override
        public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
            selected = searchHistories.get(position);
            SimpleAlertDialogFragment dialogFragment = SimpleAlertDialogFragment.newInstance(
                    "確認",
                    String.format("次の検索履歴を削除します\n%s", selected.getQuery()),
                    "OK",
                    "キャンセル"
            );
            dialogFragment.setTargetFragment(this, 0);
            dialogFragment.show(getChildFragmentManager(), "dialog");
            return true;
        }

        @Override
        //DialogInterface.OnClickListener
        public void onClick(DialogInterface dialog, int which) {
            if (selected != null && which == DialogInterface.BUTTON_POSITIVE) {
                getService().getDatabase().deleteRecord(selected);
                Toast.makeText(getActivity(), "削除しました", Toast.LENGTH_LONG).show();
                reloadHistory();
            }
        }
    }

    public static class TrendFragment extends SearchChildFragment {
        private ArrayList<String> list;
        private ThrowableAsyncTask<Void, Void, Trend[]> downloadTask;

        @Override
        public void onActivityCreated(Bundle savedInstanceState) {
            super.onActivityCreated(savedInstanceState);

            if (savedInstanceState != null) {
                list = savedInstanceState.getStringArrayList("trends");
            }

            if (list == null) {
                downloadTask = new ThrowableAsyncTask<Void, Void, Trend[]>() {
                    @Override
                    protected ThrowableResult<Trend[]> doInBackground(Void... params) {
                        try {
                            Twitter twitter = getServiceAwait().getTwitterOrPrimary(null);
                            if (twitter == null) {
                                return new ThrowableResult<>(new IllegalStateException("サービス通信エラー"));
                            }
                            return new ThrowableResult<>(twitter.getPlaceTrends(1118370).getTrends());
                        } catch (TwitterException e) {
                            e.printStackTrace();
                            return new ThrowableResult<>(e);
                        }
                    }

                    @Override
                    protected void onPostExecute(ThrowableResult<Trend[]> throwableResult) {
                        downloadTask = null;
                        if (isCancelled()) return;
                        if (!throwableResult.isException() && throwableResult.getResult() != null) {
                            list = new ArrayList<>();
                            for (Trend t : throwableResult.getResult()) {
                                list.add(t.getName());
                            }
                            setListAdapter(new ArrayAdapter<>(getActivity(), android.R.layout.simple_list_item_1, list));
                        }
                        else {
                            Exception e = throwableResult.getException();
                            if (e instanceof TwitterException) {
                                TwitterException te = (TwitterException) e;
                                Toast.makeText(getActivity(),
                                        String.format("Trendsの取得中にエラー: %d\n%s",
                                                te.getErrorCode(),
                                                te.getErrorMessage()),
                                        Toast.LENGTH_LONG).show();
                            }
                            else {
                                Toast.makeText(getActivity(),
                                        String.format("Trendsの取得中にエラー: \n%s",
                                                e.getMessage()),
                                        Toast.LENGTH_LONG).show();
                            }
                        }
                    }
                };
                downloadTask.executeParallel();
            }
            else {
                setListAdapter(new ArrayAdapter<>(getActivity(), android.R.layout.simple_list_item_1, list));
            }
        }

        @Override
        public void onListItemClick(ListView l, View v, int position, long id) {
            super.onListItemClick(l, v, position, id);
            String query = (String) getListAdapter().getItem(position);
            getParent().sendQuery(query, false);
        }

        @Override
        public void onSaveInstanceState(Bundle outState) {
            super.onSaveInstanceState(outState);
            if (list != null) {
                outState.putStringArrayList("trends", list);
            }
        }

        @Override
        public void onDestroyView() {
            super.onDestroyView();
            if (downloadTask != null) {
                downloadTask.cancel(true);
            }
        }
    }

    public static class SavedSearchFragment extends SearchChildFragment
            implements AdapterView.OnItemLongClickListener, DialogInterface.OnClickListener {
        private ArrayList<SavedSearch> savedSearches;
        private SavedSearch selected;
        private SavedSearchFragment.SavedSearchAdapter adapter;

        private ThrowableAsyncTask<Void, Void, ResponseList<SavedSearch>> downloadTask;

        @Override
        public void onActivityCreated(Bundle savedInstanceState) {
            super.onActivityCreated(savedInstanceState);

            if (savedInstanceState != null) {
                savedSearches = (ArrayList<SavedSearch>) savedInstanceState.getSerializable("saved");
            }

            if (savedSearches == null) {
                downloadTask = new ThrowableAsyncTask<Void, Void, ResponseList<SavedSearch>>() {
                    @Override
                    protected ThrowableResult<ResponseList<SavedSearch>> doInBackground(Void... params) {
                        try {
                            Twitter twitter = getServiceAwait().getTwitterOrPrimary(null);
                            if (twitter == null) {
                                return new ThrowableResult<>(new IllegalStateException("サービス通信エラー"));
                            }
                            return new ThrowableResult<>(twitter.getSavedSearches());
                        } catch (TwitterException e) {
                            e.printStackTrace();
                            return new ThrowableResult<>(e);
                        }
                    }

                    @Override
                    protected void onPostExecute(ThrowableResult<ResponseList<SavedSearch>> result) {
                        downloadTask = null;
                        if (isCancelled()) return;
                        if (!result.isException() && result.getResult() != null) {
                            ArrayList<SavedSearch> ss = new ArrayList<>(result.getResult());
                            SavedSearchFragment.this.savedSearches = ss;
                            adapter = new SavedSearchAdapter(getActivity(), ss);
                            setListAdapter(adapter);
                        }
                        else {
                            Exception e = result.getException();
                            if (e instanceof TwitterException) {
                                TwitterException te = (TwitterException) e;
                                Toast.makeText(getActivity(),
                                        String.format("保存した検索の取得中にエラー: %d\n%s",
                                                te.getErrorCode(),
                                                te.getErrorMessage()),
                                        Toast.LENGTH_LONG).show();
                            }
                            else {
                                Toast.makeText(getActivity(),
                                        String.format("保存した検索の取得中にエラー: \n%s",
                                                e.getMessage()),
                                        Toast.LENGTH_LONG).show();
                            }
                        }
                    }
                };
                downloadTask.executeParallel();
            }
            else {
                adapter = new SavedSearchAdapter(getActivity(), savedSearches);
                setListAdapter(adapter);
            }

            getListView().setOnItemLongClickListener(this);
        }

        @Override
        public void onSaveInstanceState(Bundle outState) {
            super.onSaveInstanceState(outState);
            outState.putSerializable("saved", savedSearches);
        }

        @Override
        public void onDestroyView() {
            super.onDestroyView();
            if (downloadTask != null) {
                downloadTask.cancel(true);
            }
        }

        @Override
        public void onListItemClick(ListView l, View v, int position, long id) {
            super.onListItemClick(l, v, position, id);
            getParent().sendQuery(savedSearches.get(position).getQuery(), true);
        }

        @Override
        public boolean onItemLongClick(AdapterView<?> adapterView, View view, int i, long l) {
            selected = savedSearches.get(i);
            SimpleAlertDialogFragment dialogFragment = SimpleAlertDialogFragment.newInstance(
                    "確認",
                    String.format("次の保存された検索を削除します\n%s", selected.getName()),
                    "OK",
                    "キャンセル"
            );
            dialogFragment.setTargetFragment(this, 0);
            dialogFragment.show(getChildFragmentManager(), "dialog");
            return true;
        }

        @Override
        //DialogInterface.OnClickListener
        public void onClick(DialogInterface dialogInterface, int i) {
            if (selected != null && i == DialogInterface.BUTTON_POSITIVE) {
                TwitterAsyncTask<SavedSearch> task = new TwitterAsyncTask<SavedSearch>(getActivity().getApplicationContext()) {
                    private SavedSearch savedSearch;

                    @Override
                    protected TwitterException doInBackground(SavedSearch... savedSearches) {
                        savedSearch = savedSearches[0];
                        try {
                            Twitter twitter = getServiceAwait().getTwitterOrPrimary(null);
                            if (twitter != null) {
                                twitter.destroySavedSearch(savedSearch.getId());
                            }
                        } catch (TwitterException e) {
                            e.printStackTrace();
                            return e;
                        }
                        return null;
                    }

                    @Override
                    protected void onPostExecute(TwitterException e) {
                        super.onPostExecute(e);
                        if (e == null) {
                            Toast.makeText(getActivity(), "削除しました", Toast.LENGTH_LONG).show();
                            if (!isDetached() && adapter != null) {
                                savedSearches.remove(savedSearch);
                                adapter.notifyDataSetChanged();
                            }
                        }
                    }
                };
                task.execute(selected);
            }
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
