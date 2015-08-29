package shibafu.yukari.common;

import android.util.Log;
import shibafu.yukari.database.MuteConfig;
import shibafu.yukari.twitter.statusimpl.PreformedStatus;
import twitter4j.User;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Created by shibafu on 14/04/28.
 */
public class Suppressor {
    private List<MuteConfig> configs;
    private LongList blockedIDs = new LongList();
    private LongList mutedIDs = new LongList();
    private LongList noRetweetIDs = new LongList();

    private static final Comparator<Long> COMPARATOR = (lhs, rhs) -> {
        if (lhs.equals(rhs)) return 0;
        else if (lhs > rhs) return 1;
        else return -1;
    };

    public void setConfigs(List<MuteConfig> configs) {
        this.configs = configs;
        Log.d("Suppressor", "Loaded " + configs.size() + " MuteConfigs");
    }

    public List<MuteConfig> getConfigs() {
        return configs;
    }

    public void addBlockedIDs(long[] ids) {
        blockedIDs.addAll(ids);
        Collections.sort(blockedIDs, COMPARATOR);
    }

    public void removeBlockedID(long id) {
        blockedIDs.remove(id);
        Collections.sort(blockedIDs, COMPARATOR);
    }

    public void addMutedIDs(long[] ids) {
        mutedIDs.addAll(ids);
        Collections.sort(mutedIDs, COMPARATOR);
    }

    public void addNoRetweetIDs(long[] ids) {
        noRetweetIDs.addAll(ids);
        Collections.sort(noRetweetIDs, COMPARATOR);
    }

    public boolean[] decision(PreformedStatus status) {
        boolean[] result = new boolean[7];
        if (blockedIDs.binarySearch(status.getSourceUser().getId()) ||
                mutedIDs.binarySearch(status.getSourceUser().getId())) {
            result[MuteConfig.MUTE_TWEET_RTED] = true;
            return result;
        } else if (noRetweetIDs.binarySearch(status.getUser().getId())) {
            result[MuteConfig.MUTE_RETWEET] = true;
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
                    try {
                        Pattern pattern = Pattern.compile(config.getQuery());
                        Matcher matcher = pattern.matcher(source);
                        match = matcher.find();
                    } catch (PatternSyntaxException ignore) {}
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
        if (blockedIDs.binarySearch(user.getId()) ||
                mutedIDs.binarySearch(user.getId())) {
            result[MuteConfig.MUTE_TWEET_RTED] = true;
            return result;
        } else if (noRetweetIDs.binarySearch(user.getId())) {
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
                    try {
                        Pattern pattern = Pattern.compile(config.getQuery());
                        Matcher matcher = pattern.matcher(source);
                        match = matcher.find();
                    } catch (PatternSyntaxException ignore) {}
                    break;
            }
            if (match) {
                result[config.getMute()] = true;
            }
        }
        return result;
    }

    private class LongList extends ArrayList<Long> {
        public boolean addAll(long[] array) {
            for (long l : array) {
                add(l);
            }
            return true;
        }

        public boolean binarySearch(long l) {
            return Collections.binarySearch(this, l, COMPARATOR) > -1;
        }
    }
}
