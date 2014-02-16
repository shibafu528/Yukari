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
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.ListFragment;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.PagerTabStrip;
import android.support.v4.view.ViewPager;
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

import org.w3c.dom.Text;

import java.util.ArrayList;
import java.util.List;

import shibafu.yukari.R;
import shibafu.yukari.service.TwitterService;
import shibafu.yukari.service.TwitterServiceDelegate;
import twitter4j.ResponseList;
import twitter4j.Trend;
import twitter4j.Trends;
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
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);

        Dialog dialog = new Dialog(getActivity());

        dialog.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        dialog.getWindow().setBackgroundDrawableResource(R.drawable.dialog_full_holo_light);

        dialog.setContentView(inflateView(getActivity().getLayoutInflater()));

        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialogInterface) {
                imm.showSoftInput(searchQuery, InputMethodManager.SHOW_IMPLICIT);
            }
        });

        return dialog;
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

        /*adapter = new SectionsPagerAdapter(getChildFragmentManager());

        viewPager = (ViewPager) v.findViewById(R.id.pager);
        viewPager.setAdapter(adapter);
        viewPager.setCurrentItem(1);

        PagerTabStrip tabStrip = (PagerTabStrip) v.findViewById(R.id.pager_title_strip);
        tabStrip.setDrawFullUnderline(true);
        tabStrip.setTabIndicatorColorResource(R.color.key_color);*/

        TrendFragment trendFragment = new TrendFragment();
        trendFragment.setTargetFragment(this, 0);
        FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
        transaction.add(R.id.frame, trendFragment).commit();

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
            parent = (SearchDialogFragment) getTargetFragment();
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
        @Override
        public void onActivityCreated(Bundle savedInstanceState) {
            super.onActivityCreated(savedInstanceState);

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
                    super.onPostExecute(trends);
                    if (trends != null) {
                        List<String> list = new ArrayList<String>();
                        for (Trend t : trends) {
                            list.add(t.getName());
                        }
                        setListAdapter(new ArrayAdapter<String>(getActivity(), android.R.layout.simple_list_item_1, list));
                    }
                }
            };
            task.execute();
        }

        @Override
        public void onListItemClick(ListView l, View v, int position, long id) {
            super.onListItemClick(l, v, position, id);
            String query = (String) getListAdapter().getItem(position);
            getParent().sendQuery(query);
        }
    }

    public class SavedSearchFragment extends SearchChildFragment {
        @Override
        public void onActivityCreated(Bundle savedInstanceState) {
            super.onActivityCreated(savedInstanceState);
            setListAdapter(new ArrayAdapter<String>(getActivity(), android.R.layout.simple_list_item_1, new String[]{""}));
        }
    }
}
