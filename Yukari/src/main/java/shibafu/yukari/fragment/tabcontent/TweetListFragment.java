package shibafu.yukari.fragment.tabcontent;

import android.content.Intent;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.List;

import shibafu.yukari.activity.StatusActivity;
import shibafu.yukari.common.TweetAdapterWrap;
import shibafu.yukari.service.TwitterService;
import shibafu.yukari.twitter.AuthUserRecord;
import shibafu.yukari.twitter.PreformedStatus;
import shibafu.yukari.twitter.RESTLoader;
import twitter4j.UserMentionEntity;

/**
 * Created by Shibafu on 13/08/01.
 */
public abstract class TweetListFragment extends TwitterListFragment<PreformedStatus> {

    //ListView Adapter Wrapper
    protected TweetAdapterWrap adapterWrap;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        adapterWrap = new TweetAdapterWrap(
                getActivity().getApplicationContext(), users, elements, PreformedStatus.class);
        setListAdapter(adapterWrap.getAdapter());
    }

    @Override
    public void onListItemClick(PreformedStatus clickedElement) {
        Intent intent = new Intent(getActivity(), StatusActivity.class);
        intent.putExtra(StatusActivity.EXTRA_STATUS, clickedElement);
        intent.putExtra(StatusActivity.EXTRA_USER, clickedElement.getReceiveUser());
        startActivity(intent);
    }

    protected int prepareInsertStatus(PreformedStatus status) {
        //自己ツイートチェック
        boolean isMyTweet = getService().isMyTweet(status) != null;
        //優先ユーザチェック
        if (!isMyTweet) {
            ArrayList<Long> mentions = new ArrayList<Long>();
            for (UserMentionEntity entity : status.getUserMentionEntities()) {
                mentions.add(entity.getId());
            }
            for (AuthUserRecord user : users) {
                //指名されている場合はそちらを優先する
                if (mentions.contains(user.NumericId)) {
                    status.setReceiveUser(user);
                    break;
                }
            }
        }
        //挿入位置の探索と追加
        PreformedStatus storedStatus;
        for (int i = 0; i < elements.size(); ++i) {
            storedStatus = elements.get(i);
            if (status.getId() == storedStatus.getId()) {
                //既に他のアカウントで受信されていた場合でも、今回の受信がプライマリアカウントによるものであれば
                //受信アカウントのフィールドを上書きする
                if (!isMyTweet && status.getReceiveUser().isPrimary && !storedStatus.getReceiveUser().isPrimary) {
                    storedStatus.setReceiveUser(status.getReceiveUser());
                }
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
        public void notifyDataSetChanged() {
            adapterWrap.notifyDataSetChanged();
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
