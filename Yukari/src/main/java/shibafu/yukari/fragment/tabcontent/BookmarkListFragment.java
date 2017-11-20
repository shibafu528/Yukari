package shibafu.yukari.fragment.tabcontent;

import com.annimon.stream.Collectors;
import com.annimon.stream.Optional;
import com.annimon.stream.Stream;
import shibafu.yukari.common.Suppressor;
import shibafu.yukari.common.async.ParallelAsyncTask;
import shibafu.yukari.database.Bookmark;
import shibafu.yukari.database.MuteConfig;
import shibafu.yukari.database.UserExtras;
import shibafu.yukari.twitter.AuthUserRecord;
import shibafu.yukari.twitter.statusimpl.PreformedStatus;
import shibafu.yukari.twitter.statusmanager.StatusManager;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by shibafu on 14/02/13.
 */
public class BookmarkListFragment extends TweetListFragment {

    @Override
    protected void executeLoader(int requestMode, AuthUserRecord userRecord) {
        BookmarkLoader loader = new BookmarkLoader();
        switch (requestMode) {
            case LOADER_LOAD_UPDATE:
                clearUnreadNotifier();
            case LOADER_LOAD_INIT:
                loader.execute();
                break;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (isTwitterServiceBound() && !elements.isEmpty()) {
            executeLoader(LOADER_LOAD_UPDATE, null);
        }
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        executeLoader(LOADER_LOAD_INIT, null);
    }

    @Override
    public void onServiceDisconnected() {}

    @Override
    public boolean isCloseable() {
        return true;
    }

    private class BookmarkLoader extends ParallelAsyncTask<Void, Void, List<Bookmark>> {

        @Override
        protected List<Bookmark> doInBackground(Void... params) {
            if (!isTwitterServiceBound() || getTwitterService() == null || getTwitterService().getDatabase() == null) return new ArrayList<>();
            return getTwitterService().getDatabase().getBookmarks();
        }

        @Override
        protected void onPreExecute() {
            changeFooterProgress(true);
        }

        @Override
        protected void onPostExecute(List<Bookmark> bookmarks) {
            // はい、調子乗ってブクマ見るやろ お前ほんまに覚えとけよ ガチで消したるからな
            // ほんまにキレタ 絶対許さん お前のID控えたからな
            List<Long> shownIDs = Stream.of(elements).map(twitter4j.Status::getId).collect(Collectors.toList());
            List<Long> stashedIDs = Stream.of(stash).map(twitter4j.Status::getId).collect(Collectors.toList());

            Suppressor suppressor = getTwitterService().getSuppressor();
            List<UserExtras> userExtras = getTwitterService().getUserExtras();

            boolean[] mute;
            for (Bookmark status : bookmarks) {
                AuthUserRecord checkOwn = getTwitterService().isMyTweet(status);
                if (checkOwn != null) {
                    status.setOwner(checkOwn);
                } else {
                    Optional<UserExtras> first = Stream.of(userExtras).filter(ue -> ue.getId() == status.getSourceUser().getId()).findFirst();
                    if (first.isPresent() && first.get().getPriorityAccount() != null) {
                        status.setOwner(first.get().getPriorityAccount());
                    }
                }

                mute = suppressor.decision(status);
                if (mute[MuteConfig.MUTE_IMAGE_THUMB]) {
                    status.setCensoredThumbs(true);
                }

                if (!(  mute[MuteConfig.MUTE_TWEET_RTED] ||
                        (!status.isRetweet() && mute[MuteConfig.MUTE_TWEET]) ||
                        (status.isRetweet() && mute[MuteConfig.MUTE_RETWEET]))) {
                    insertElement(status);

                    if (shownIDs.contains(status.getId())) {
                        shownIDs.remove(status.getId());
                    }
                } else {
                    stash.add(status);

                    if (stashedIDs.contains(status.getId())) {
                        stashedIDs.remove(status.getId());
                    }
                }

                StatusManager.getReceivedStatuses().put(status.getId(), status);
            }

            List<PreformedStatus> removeStatuses = new ArrayList<>();
            // ConcurrentModify対策で別のコレクションに移す
            for (PreformedStatus status : elements) {
                if (shownIDs.contains(status.getId())) {
                    removeStatuses.add(status);
                }
            }
            for (PreformedStatus status : removeStatuses) {
                deleteElement(status);
            }

            for (Iterator<PreformedStatus> iterator = stash.iterator(); iterator.hasNext(); ) {
                if (stashedIDs.contains(iterator.next().getId())) {
                    iterator.remove();
                }
            }

            changeFooterProgress(false);
            setRefreshComplete();
        }
    }

}
