package shibafu.yukari.linkage;

import androidx.annotation.NonNull;
import info.shibafu528.yukari.processor.messagequeue.MessageQueue;
import info.shibafu528.yukari.processor.messagequeue.PassThrough;
import shibafu.yukari.database.AutoMuteConfig;
import shibafu.yukari.entity.NotifyKind;
import shibafu.yukari.entity.Status;
import shibafu.yukari.entity.User;
import shibafu.yukari.twitter.entity.TwitterMessage;

import java.util.List;

@MessageQueue
public interface TimelineHub {
    @PassThrough
    void setAutoMuteConfigs(@NonNull List<AutoMuteConfig> autoMuteConfigs);
    @PassThrough
    void addObserver(@NonNull TimelineObserver observer);
    @PassThrough
    void removeObserver(@NonNull TimelineObserver observer);

    void onStatus(@NonNull String timelineId, @NonNull Status status, boolean passive);
    void onDirectMessage(@NonNull String timelineId, @NonNull TwitterMessage status, boolean passive);
    void onRestRequestCompleted(@NonNull String timelineId, long taskKey);
    void onRestRequestCancelled(@NonNull String timelineId, long taskKey);
    void onNotify(@NotifyKind int kind, @NonNull User eventBy, @NonNull Status status);
    void onFavorite(@NonNull User from, @NonNull Status status);
    void onUnfavorite(@NonNull User from, @NonNull Status status);
    void onDelete(@NonNull String providerHost, long id);
    void onWipe();
    void onForceUpdateUI();
}
