package shibafu.yukari.fragment.tabcontent;

import android.content.Intent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import shibafu.yukari.activity.StatusActivity;
import shibafu.yukari.common.Suppressor;
import shibafu.yukari.database.MuteConfig;
import shibafu.yukari.service.TwitterService;
import shibafu.yukari.twitter.AuthUserRecord;
import shibafu.yukari.twitter.RESTLoader;
import shibafu.yukari.twitter.statusimpl.PreformedStatus;

/**
 * Created by Shibafu on 13/08/01.
 */
public abstract class TweetListFragment extends TwitterListFragment<PreformedStatus> {

    //Mute Stash
    protected ArrayList<PreformedStatus> stash = new ArrayList<>();

    private List<MuteConfig> previewMuteConfig;

    public TweetListFragment() {
        super(PreformedStatus.class);
    }

    @Override
    public void onListItemClick(PreformedStatus clickedElement) {
        Intent intent = new Intent(getActivity(), StatusActivity.class);
        intent.putExtra(StatusActivity.EXTRA_STATUS, clickedElement);
        intent.putExtra(StatusActivity.EXTRA_USER, clickedElement.getRepresentUser());
        startActivity(intent);
    }

    @Override
    public void onResume() {
        super.onResume();

        if (isServiceBound()) {
            List<MuteConfig> configs = getService().getSuppressor().getConfigs();
            if (previewMuteConfig != null && previewMuteConfig != configs) {
                previewMuteConfig = configs;

                getHandler().postDelayed(onReloadMuteConfigs, 100);
            }
        }
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        List<MuteConfig> configs = getService().getSuppressor().getConfigs();
        if (previewMuteConfig == null) {
            previewMuteConfig = configs;
        }
        else if (previewMuteConfig != configs) {
            previewMuteConfig = configs;

            getHandler().post(onReloadMuteConfigs);
        }
    }

    private Runnable onReloadMuteConfigs = new Runnable() {
        @Override
        public void run() {
            Suppressor suppressor = getService().getSuppressor();
            boolean[] mute;
            for (Iterator<PreformedStatus> it = elements.iterator(); it.hasNext(); ) {
                PreformedStatus s = it.next();

                mute = suppressor.decision(s);
                s.setCensoredThumbs(mute[MuteConfig.MUTE_IMAGE_THUMB]);

                if ((mute[MuteConfig.MUTE_TWEET_RTED] ||
                        (!s.isRetweet() && mute[MuteConfig.MUTE_TWEET]) ||
                        (s.isRetweet() && mute[MuteConfig.MUTE_RETWEET]))) {
                    stash.add(s);
                    it.remove();
                }
            }
            for (Iterator<PreformedStatus> it = stash.iterator(); it.hasNext(); ) {
                PreformedStatus s = it.next();

                mute = suppressor.decision(s);
                s.setCensoredThumbs(mute[MuteConfig.MUTE_IMAGE_THUMB]);

                if (!(mute[MuteConfig.MUTE_TWEET_RTED] ||
                        (!s.isRetweet() && mute[MuteConfig.MUTE_TWEET]) ||
                        (s.isRetweet() && mute[MuteConfig.MUTE_RETWEET]))) {
                    int position = prepareInsertStatus(s);
                    if (position > -1) {
                        elements.add(position, s);
                        it.remove();
                    }
                }
            }
            notifyDataSetChanged();
        }
    };

    @Override
    protected int prepareInsertStatus(PreformedStatus status) {
        //自己ツイートチェック
        AuthUserRecord owner = getService().isMyTweet(status);
        if (owner != null) {
            status.setOwner(owner);
        }
        //挿入位置の探索と追加
        PreformedStatus storedStatus;
        for (int i = 0; i < elements.size(); ++i) {
            storedStatus = elements.get(i);
            if (status.getId() == storedStatus.getId()) {
                storedStatus.merge(status);
                return -1;
            }
            else if (status.getId() > storedStatus.getId()) {
                return i;
            }
        }
        return elements.size();
    }

    private RESTLoader.RESTLoaderInterface defaultRESTInterface = new RESTLoader.RESTLoaderInterface() {
        @Override
        public TwitterService getService() {
            return TweetListFragment.this.getService();
        }

        @Override
        public List<PreformedStatus> getStatuses() {
            return elements;
        }

        @Override
        public List<PreformedStatus> getStash() {
            return stash;
        }

        @Override
        public void notifyDataSetChanged() {
            TweetListFragment.this.notifyDataSetChanged();
        }

        @Override
        public int prepareInsertStatus(PreformedStatus status) {
            return TweetListFragment.this.prepareInsertStatus(status);
        }

        @Override
        public void changeFooterProgress(boolean isLoading) {
            TweetListFragment.this.changeFooterProgress(isLoading);
        }
    };

    protected RESTLoader.RESTLoaderInterface getDefaultRESTInterface() {
        return defaultRESTInterface;
    }

}
