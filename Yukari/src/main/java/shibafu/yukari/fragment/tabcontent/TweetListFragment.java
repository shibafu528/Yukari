package shibafu.yukari.fragment.tabcontent;

import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.MotionEvent;
import android.view.View;

import com.nineoldandroids.animation.ObjectAnimator;
import com.nineoldandroids.animation.PropertyValuesHolder;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import shibafu.yukari.R;
import shibafu.yukari.activity.StatusActivity;
import shibafu.yukari.activity.TweetActivity;
import shibafu.yukari.common.Suppressor;
import shibafu.yukari.common.TweetAdapterWrap;
import shibafu.yukari.common.async.TwitterAsyncTask;
import shibafu.yukari.database.MuteConfig;
import shibafu.yukari.service.TwitterService;
import shibafu.yukari.twitter.AuthUserRecord;
import shibafu.yukari.twitter.RESTLoader;
import shibafu.yukari.twitter.statusimpl.PreformedStatus;
import twitter4j.TwitterException;

/**
 * Created by Shibafu on 13/08/01.
 */
public abstract class TweetListFragment extends TwitterListFragment<PreformedStatus> {

    //Mute Stash
    protected ArrayList<PreformedStatus> stash = new ArrayList<>();

    //SwipeAction PopupWindow
    private static final int SWIPE_ACTION_CANCEL = 0;
    private static final int SWIPE_ACTION_REPLY = 1;
    private static final int SWIPE_ACTION_REPLY_ALL = 2;
    private static final int SWIPE_ACTION_FAVORITE = 3;
    private static final int SWIPE_ACTION_RETWEET = 4;
    private static final int SWIPE_ACTION_FAVRT = 5;

    private static final int SWIPE_ACTION_THRESHOLD = 120;

    private float swipeActionDownPositionX, swipeActionDownPositionY;
    private int swipeActionSelected = 0;
    private PreformedStatus swipeActionStatusGrabbed;

    private List<MuteConfig> previewMuteConfig;

    public TweetListFragment() {
        super(PreformedStatus.class);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (swipeActionStatusView != null) {
            switch (PreferenceManager.getDefaultSharedPreferences(getActivity()).getString("pref_theme", "light")) {
                case "light":
                    swipeActionStatusView.setBackgroundResource(R.drawable.dialog_full_holo_light);
                    break;
                case "dark":
                    swipeActionStatusView.setBackgroundResource(R.drawable.dialog_full_holo_dark);
                    break;
            }
            swipeActionStatusView.setVisibility(View.INVISIBLE);

            if (PreferenceManager.getDefaultSharedPreferences(getActivity()).getBoolean("pref_extended_touch_event", false)) {
                class LocalFunction {
                    void requestDisallowInterceptTouchEvent() {
                        disableReloadTemp();
                        getListView().requestDisallowInterceptTouchEvent(true);
                        if (getView() != null) {
                            getView().getParent().requestDisallowInterceptTouchEvent(true);
                        }
                    }

                    boolean onStatusGrabbed(MotionEvent event) {
                        requestDisallowInterceptTouchEvent();
                        switch (event.getAction()) {
                            case MotionEvent.ACTION_MOVE:
                                float moveX = event.getRawX() - swipeActionDownPositionX;
                                float moveY = Math.abs(event.getRawY() - swipeActionDownPositionY);
                                if (moveX < SWIPE_ACTION_THRESHOLD && moveY < SWIPE_ACTION_THRESHOLD) {
                                    swipeActionSelected = SWIPE_ACTION_CANCEL;
                                } else if (moveX > moveY * 3) {
                                    swipeActionSelected = SWIPE_ACTION_FAVORITE;
                                } else if (moveX > moveY * 2) {
                                    swipeActionSelected = SWIPE_ACTION_FAVRT;
                                } else if (moveY > moveX * 3) {
                                    swipeActionSelected = SWIPE_ACTION_RETWEET;
                                } else if (moveY > moveX * 2) {
                                    swipeActionSelected = SWIPE_ACTION_REPLY;
                                }
                                switch(swipeActionSelected) {
                                    case SWIPE_ACTION_CANCEL:
                                        swipeActionInfoLabel.setText(swipeActionStatusGrabbed.getRepresentUser().ScreenName + " >> Cancel");
                                        break;
                                    case SWIPE_ACTION_REPLY:
                                        swipeActionInfoLabel.setText(swipeActionStatusGrabbed.getRepresentUser().ScreenName + " >> Reply");
                                        break;
                                    case SWIPE_ACTION_FAVORITE:
                                        swipeActionInfoLabel.setText(swipeActionStatusGrabbed.getRepresentUser().ScreenName + " >> Favorite");
                                        break;
                                    case SWIPE_ACTION_RETWEET:
                                        swipeActionInfoLabel.setText(swipeActionStatusGrabbed.getRepresentUser().ScreenName + " >> Retweet");
                                        break;
                                    case SWIPE_ACTION_FAVRT:
                                        swipeActionInfoLabel.setText(swipeActionStatusGrabbed.getRepresentUser().ScreenName + " >> Fav & RT");
                                        break;
                                }
                                break;
                            case MotionEvent.ACTION_UP:
                                switch(swipeActionSelected) {
                                    case SWIPE_ACTION_REPLY: {
                                        Intent intent = new Intent(getActivity(), TweetActivity.class);
                                        intent.putExtra(TweetActivity.EXTRA_USER, swipeActionStatusGrabbed.getRepresentUser());
                                        intent.putExtra(TweetActivity.EXTRA_STATUS, swipeActionStatusGrabbed.getOriginStatus());
                                        intent.putExtra(TweetActivity.EXTRA_MODE, TweetActivity.MODE_REPLY);
                                        intent.putExtra(TweetActivity.EXTRA_TEXT, "@" +
                                                swipeActionStatusGrabbed.getSourceUser().getScreenName() + " ");
                                        startActivity(intent);
                                        break;
                                    }
                                    case SWIPE_ACTION_FAVORITE:
                                        new TwitterAsyncTask<PreformedStatus>(getActivity()) {
                                            @Override
                                            protected TwitterException doInBackground(PreformedStatus... params) {
                                                getTwitterService().createFavorite(params[0].getRepresentUser(), params[0].getOriginStatus().getId());
                                                return null;
                                            }
                                        }.execute(swipeActionStatusGrabbed);
                                        break;
                                    case SWIPE_ACTION_RETWEET:
                                        new TwitterAsyncTask<PreformedStatus>(getActivity()) {
                                            @Override
                                            protected TwitterException doInBackground(PreformedStatus... params) {
                                                getTwitterService().retweetStatus(params[0].getRepresentUser(), params[0].getOriginStatus().getId());
                                                return null;
                                            }
                                        }.execute(swipeActionStatusGrabbed);
                                        break;
                                    case SWIPE_ACTION_FAVRT:
                                        new TwitterAsyncTask<PreformedStatus>(getActivity()) {
                                            @Override
                                            protected TwitterException doInBackground(PreformedStatus... params) {
                                                getTwitterService().createFavorite(params[0].getRepresentUser(), params[0].getOriginStatus().getId());
                                                getTwitterService().retweetStatus(params[0].getRepresentUser(), params[0].getOriginStatus().getId());
                                                return null;
                                            }
                                        }.execute(swipeActionStatusGrabbed);
                                        break;
                                }
                                dismissSwipeActionStatusView();
                                break;
                        }
                        return true;
                    }
                }
                final LocalFunction local = new LocalFunction();

                adapterWrap.setOnTouchProfileImageIconListener((element, v, event) -> {
                    if (swipeActionStatusGrabbed != null) {
                        return local.onStatusGrabbed(event);
                    }

                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        local.requestDisallowInterceptTouchEvent();

                        PreformedStatus status = (PreformedStatus) element;
                        TweetAdapterWrap.ViewConverter viewConverter = TweetAdapterWrap.ViewConverter.newInstance(
                                getActivity(),
                                status.getRepresentUser().toSingleList(),
                                null,
                                PreferenceManager.getDefaultSharedPreferences(getActivity()),
                                PreformedStatus.class);
                        viewConverter.convertView(swipeActionStatusView.findViewById(R.id.swipeActionStatus),
                                status, TweetAdapterWrap.ViewConverter.MODE_DEFAULT);

                        swipeActionStatusView.setVisibility(View.VISIBLE);

                        Rect rectInGlobal = new Rect();
                        v.getGlobalVisibleRect(rectInGlobal);

                        ObjectAnimator.ofPropertyValuesHolder(swipeActionStatusView,
                                PropertyValuesHolder.ofFloat("translationY", rectInGlobal.top, 0f),
                                PropertyValuesHolder.ofFloat("alpha", 0f, 0.9f))
                                .setDuration(150)
                                .start();

                        swipeActionStatusGrabbed = status;
                        swipeActionDownPositionX = event.getRawX();
                        swipeActionDownPositionY = event.getRawY();
                        return true;
                    }
                    return false;
                });

                listView.setOnTouchListener((v, event) -> {
                    if (swipeActionStatusGrabbed != null) {
                        return local.onStatusGrabbed(event);
                    }
                    return false;
                });
            }
        }
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
    public void onPause() {
        super.onPause();
        dismissSwipeActionStatusView();
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

    private void dismissSwipeActionStatusView() {
        enableReload();
        if (swipeActionStatusView != null) {
            ObjectAnimator.ofFloat(swipeActionStatusView, "alpha", 0.9f, 0f)
                    .setDuration(150)
                    .start();
            swipeActionStatusView.setVisibility(View.INVISIBLE);
            swipeActionStatusGrabbed = null;
        }
    }

    private Runnable onReloadMuteConfigs = () -> {
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
                getHandler().post(this::notifyDataSetChanged);
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
