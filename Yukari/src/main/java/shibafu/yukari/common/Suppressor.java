package shibafu.yukari.common;

import android.support.v4.util.LongSparseArray;
import android.util.Log;
import org.eclipse.collections.api.list.primitive.MutableLongList;
import org.eclipse.collections.impl.list.mutable.primitive.LongArrayList;
import shibafu.yukari.database.MuteConfig;
import shibafu.yukari.database.MuteMatch;
import shibafu.yukari.twitter.statusimpl.PreformedStatus;
import twitter4j.User;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Created by shibafu on 14/04/28.
 */
public class Suppressor {
    private List<MuteConfig> configs;
    private MutableLongList blockedIDs = new LongArrayList();
    private MutableLongList mutedIDs = new LongArrayList();
    private MutableLongList noRetweetIDs = new LongArrayList();
    private LongSparseArray<Pattern> patternCache = new LongSparseArray<>();

    public void setConfigs(List<MuteConfig> configs) {
        this.configs = configs;
        patternCache.clear();
        for (MuteConfig config : configs) {
            if (!config.expired() && config.getMatch() == MuteMatch.MATCH_REGEX) {
                try {
                    patternCache.put(config.getId(), Pattern.compile(config.getQuery()));
                } catch (PatternSyntaxException ignore) {
                    patternCache.put(config.getId(), null);
                }
            }
        }
        Log.d("Suppressor", "Loaded " + configs.size() + " MuteConfigs");
    }

    public List<MuteConfig> getConfigs() {
        return configs;
    }

    public void addBlockedIDs(long[] ids) {
        blockedIDs.addAll(ids);
        blockedIDs.sortThis();
    }

    public void removeBlockedID(long id) {
        blockedIDs.remove(id);
        blockedIDs.sortThis();
    }

    public void addMutedIDs(long[] ids) {
        mutedIDs.addAll(ids);
        mutedIDs.sortThis();
    }

    public void addNoRetweetIDs(long[] ids) {
        noRetweetIDs.addAll(ids);
        noRetweetIDs.sortThis();
    }

    public boolean[] decision(shibafu.yukari.entity.Status status) {
        boolean[] result = new boolean[7];

        for (MuteConfig config : configs) {
            if (config.expired()) continue;

            shibafu.yukari.entity.Status s;
            int mute = config.getMute();
            if (status.isRepost() && mute != MuteConfig.MUTE_RETWEET && mute != MuteConfig.MUTE_NOTIF_RT) {
                s = status.getOriginStatus();
            } else {
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
                case MuteMatch.MATCH_EXACT:
                    match = source.equals(config.getQuery());
                    break;
                case MuteMatch.MATCH_PARTIAL:
                    match = source.contains(config.getQuery());
                    break;
                case MuteMatch.MATCH_REGEX: {
                    Pattern pattern = patternCache.get(config.getId());
                    if (pattern == null && patternCache.indexOfKey(config.getId()) < 0) {
                        try {
                            pattern = Pattern.compile(config.getQuery());
                            patternCache.put(config.getId(), pattern);
                        } catch (PatternSyntaxException ignore) {
                            patternCache.put(config.getId(), null);
                        }
                    }
                    if (pattern != null) {
                        Matcher matcher = pattern.matcher(source);
                        match = matcher.find();
                    }
                    break;
                }
            }
            if (match) {
                result[mute] = true;
            }
        }
        return result;
    }

    public boolean[] decision(PreformedStatus status) {
        boolean[] result = new boolean[7];
        if (blockedIDs.binarySearch(status.getSourceUser().getId()) > -1 ||
                mutedIDs.binarySearch(status.getSourceUser().getId()) > -1) {
            result[MuteConfig.MUTE_TWEET_RTED] = true;
            return result;
        } else if (noRetweetIDs.binarySearch(status.getUser().getId()) > -1) {
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
                case MuteMatch.MATCH_EXACT:
                    match = source.equals(config.getQuery());
                    break;
                case MuteMatch.MATCH_PARTIAL:
                    match = source.contains(config.getQuery());
                    break;
                case MuteMatch.MATCH_REGEX: {
                    Pattern pattern = patternCache.get(config.getId());
                    if (pattern == null && patternCache.indexOfKey(config.getId()) < 0) {
                        try {
                            pattern = Pattern.compile(config.getQuery());
                            patternCache.put(config.getId(), pattern);
                        } catch (PatternSyntaxException ignore) {
                            patternCache.put(config.getId(), null);
                        }
                    }
                    if (pattern != null) {
                        Matcher matcher = pattern.matcher(source);
                        match = matcher.find();
                    }
                    break;
                }
            }
            if (match) {
                result[mute] = true;
            }
        }
        return result;
    }

    public boolean[] decisionUser(shibafu.yukari.entity.User user) {
        boolean[] result = new boolean[7];
        if (blockedIDs.binarySearch(user.getId()) > -1 ||
                mutedIDs.binarySearch(user.getId()) > -1) {
            result[MuteConfig.MUTE_TWEET_RTED] = true;
            return result;
        } else if (noRetweetIDs.binarySearch(user.getId()) > -1) {
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
                case MuteMatch.MATCH_EXACT:
                    match = source.equals(config.getQuery());
                    break;
                case MuteMatch.MATCH_PARTIAL:
                    match = source.contains(config.getQuery());
                    break;
                case MuteMatch.MATCH_REGEX: {
                    Pattern pattern = patternCache.get(config.getId());
                    if (pattern == null && patternCache.indexOfKey(config.getId()) < 0) {
                        try {
                            pattern = Pattern.compile(config.getQuery());
                            patternCache.put(config.getId(), pattern);
                        } catch (PatternSyntaxException ignore) {
                            patternCache.put(config.getId(), null);
                        }
                    }
                    if (pattern != null) {
                        Matcher matcher = pattern.matcher(source);
                        match = matcher.find();
                    }
                    break;
                }
            }
            if (match) {
                result[config.getMute()] = true;
            }
        }
        return result;
    }

    public boolean[] decisionUser(User user) {
        boolean[] result = new boolean[7];
        if (blockedIDs.binarySearch(user.getId()) > -1 ||
                mutedIDs.binarySearch(user.getId()) > -1) {
            result[MuteConfig.MUTE_TWEET_RTED] = true;
            return result;
        } else if (noRetweetIDs.binarySearch(user.getId()) > -1) {
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
                case MuteMatch.MATCH_EXACT:
                    match = source.equals(config.getQuery());
                    break;
                case MuteMatch.MATCH_PARTIAL:
                    match = source.contains(config.getQuery());
                    break;
                case MuteMatch.MATCH_REGEX: {
                    Pattern pattern = patternCache.get(config.getId());
                    if (pattern == null && patternCache.indexOfKey(config.getId()) < 0) {
                        try {
                            pattern = Pattern.compile(config.getQuery());
                            patternCache.put(config.getId(), pattern);
                        } catch (PatternSyntaxException ignore) {
                            patternCache.put(config.getId(), null);
                        }
                    }
                    if (pattern != null) {
                        Matcher matcher = pattern.matcher(source);
                        match = matcher.find();
                    }
                    break;
                }
            }
            if (match) {
                result[config.getMute()] = true;
            }
        }
        return result;
    }
}
