package shibafu.yukari.common;

import android.util.Log;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import shibafu.yukari.database.MuteConfig;
import shibafu.yukari.twitter.statusimpl.PreformedStatus;
import twitter4j.User;

/**
 * Created by shibafu on 14/04/28.
 */
public class Suppressor {
    private List<MuteConfig> configs;
    private List<Long> denialIDs = new ArrayList<>();
    private List<Long> noRetweetIDs = new ArrayList<>();

    private Comparator<Long> comparator = new Comparator<Long>() {
        @Override
        public int compare(Long lhs, Long rhs) {
            if (lhs.equals(rhs)) return 0;
            else if (lhs > rhs) return 1;
            else return -1;
        }
    };

    public void setConfigs(List<MuteConfig> configs) {
        this.configs = configs;
        Log.d("Suppressor", "Loaded " + configs.size() + " MuteConfigs");
    }

    public List<MuteConfig> getConfigs() {
        return configs;
    }

    public void addDenialIDs(Collection<Long> ids) {
        denialIDs.addAll(ids);
        Collections.sort(denialIDs, comparator);
    }

    public void addNoRetweetIDs(Collection<Long> ids) {
        noRetweetIDs.addAll(ids);
        Collections.sort(noRetweetIDs, comparator);
    }

    public void addNoRetweetIDs(long[] ids) {
        for (long id : ids) {
            noRetweetIDs.add(id);
        }
        Collections.sort(noRetweetIDs, comparator);
    }

    public boolean[] decision(PreformedStatus status) {
        boolean[] result = new boolean[7];
        if (Collections.binarySearch(denialIDs, status.getSourceUser().getId(), comparator) > -1) {
            result[MuteConfig.MUTE_TWEET_RTED] = true;
            return result;
        } else if (Collections.binarySearch(noRetweetIDs, status.getSourceUser().getId(), comparator) > -1) {
            result[MuteConfig.MUTE_RETWEET] = true;
            return result;
        }
        for (MuteConfig config : configs) {
            if (config.expired()) continue;

            PreformedStatus s;
            int mute = config.getMute();
            if (status.isRetweet() && mute != MuteConfig.MUTE_RETWEET && mute != MuteConfig.MUTE_NOTIF_RT) {
                s = status.getRetweetedStatus();
            }
            else {
                s = status;
            }
            String source;
            switch (config.getScope()) {
                case MuteConfig.SCOPE_TEXT:
                    source = s.getText();
                    break;
                case MuteConfig.SCOPE_USER_ID:
                    source = String.valueOf(s.getUser().getId());
                    break;
                case MuteConfig.SCOPE_USER_SN:
                    source = s.getUser().getScreenName();
                    break;
                case MuteConfig.SCOPE_USER_NAME:
                    source = s.getUser().getName();
                    break;
                case MuteConfig.SCOPE_VIA:
                    source = s.getSource();
                    break;
                default:
                    continue;
            }
            boolean match = false;
            switch (config.getMatch()) {
                case MuteConfig.MATCH_EXACT:
                    match = source.equals(config.getQuery());
                    break;
                case MuteConfig.MATCH_PARTIAL:
                    match = source.contains(config.getQuery());
                    break;
                case MuteConfig.MATCH_REGEX:
                    Pattern pattern = Pattern.compile(config.getQuery());
                    Matcher matcher = pattern.matcher(source);
                    match = matcher.find();
                    break;
            }
            if (match) {
                result[mute] = true;
            }
        }
        return result;
    }

    public boolean[] decisionUser(User user) {
        boolean[] result = new boolean[7];
        if (Collections.binarySearch(denialIDs, user.getId(), comparator) > -1) {
            result[MuteConfig.MUTE_TWEET_RTED] = true;
            return result;
        } else if (Collections.binarySearch(noRetweetIDs, user.getId(), comparator) > -1) {
            result[MuteConfig.MUTE_RETWEET] = true;
            return result;
        }
        for (MuteConfig config : configs) {
            String source;
            switch (config.getScope()) {
                case MuteConfig.SCOPE_USER_ID:
                    source = String.valueOf(user.getId());
                    break;
                case MuteConfig.SCOPE_USER_SN:
                    source = user.getScreenName();
                    break;
                case MuteConfig.SCOPE_USER_NAME:
                    source = user.getName();
                    break;
                default:
                    continue;
            }
            boolean match = false;
            switch (config.getMatch()) {
                case MuteConfig.MATCH_EXACT:
                    match = source.equals(config.getQuery());
                    break;
                case MuteConfig.MATCH_PARTIAL:
                    match = source.contains(config.getQuery());
                    break;
                case MuteConfig.MATCH_REGEX:
                    Pattern pattern = Pattern.compile(config.getQuery());
                    Matcher matcher = pattern.matcher(source);
                    match = matcher.find();
                    break;
            }
            if (match) {
                result[config.getMute()] = true;
            }
        }
        return result;
    }
}
