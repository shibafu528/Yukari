package shibafu.yukari.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.RemoteInput;

import shibafu.yukari.activity.TweetActivity;
import shibafu.yukari.common.TweetDraft;
import shibafu.yukari.service.PostService;
import shibafu.yukari.twitter.AuthUserRecord;
import shibafu.yukari.twitter.statusimpl.PreformedStatus;

/**
 * Created by shibafu on 15/01/14.
 */
public class VoiceReplyReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Bundle input = RemoteInput.getResultsFromIntent(intent);
        if (input != null) {
            AuthUserRecord user = (AuthUserRecord) intent.getSerializableExtra(TweetActivity.EXTRA_USER);
            int mode = intent.getIntExtra(TweetActivity.EXTRA_MODE, 0);
            String prefix = intent.getStringExtra(TweetActivity.EXTRA_TEXT);
            CharSequence voiceInput = input.getCharSequence(Intent.EXTRA_TEXT);

            TweetDraft.Builder builder = new TweetDraft.Builder()
                    .addWriter(user);

            if (mode == TweetActivity.MODE_DM) {
                String targetSN = intent.getStringExtra(TweetActivity.EXTRA_DM_TARGET_SN);
                long inReplyToId = intent.getLongExtra(TweetActivity.EXTRA_IN_REPLY_TO, -1);

                builder.setMessageTarget(targetSN)
                        .setInReplyTo(inReplyToId)
                        .setDirectMessage(true)
                        .setText(String.valueOf(voiceInput));
            } else {
                PreformedStatus inReplyToStatus = (PreformedStatus) intent.getSerializableExtra(TweetActivity.EXTRA_STATUS);

                builder.setInReplyTo(inReplyToStatus.getId())
                        .setText(prefix + voiceInput);
            }

            Intent postIntent = PostService.newIntent(context, builder.build());
            context.startService(postIntent);
        }
    }
}
