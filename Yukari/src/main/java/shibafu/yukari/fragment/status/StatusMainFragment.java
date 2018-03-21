package shibafu.yukari.fragment.status;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v7.widget.PopupMenu;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.Toast;
import lombok.AllArgsConstructor;
import shibafu.yukari.R;
import shibafu.yukari.activity.AccountChooserActivity;
import shibafu.yukari.activity.MainActivity;
import shibafu.yukari.activity.StatusActivity;
import shibafu.yukari.activity.TweetActivity;
import shibafu.yukari.common.StatusChildUI;
import shibafu.yukari.common.StatusUI;
import shibafu.yukari.common.TweetDraft;
import shibafu.yukari.common.async.ThrowableTwitterAsyncTask;
import shibafu.yukari.common.bitmapcache.ImageLoaderTask;
import shibafu.yukari.fragment.base.TwitterFragment;
import shibafu.yukari.service.AsyncCommandService;
import shibafu.yukari.service.PostService;
import shibafu.yukari.service.TwitterService;
import shibafu.yukari.twitter.AuthUserRecord;
import shibafu.yukari.twitter.TwitterUtil;
import shibafu.yukari.twitter.statusimpl.PreformedStatus;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.UserMentionEntity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by Shibafu on 13/08/02.
 */
public class StatusMainFragment extends TwitterFragment implements StatusChildUI {

    private static final int REQUEST_REPLY     = 0x00;
    private static final int REQUEST_RETWEET   = 0x01;
    private static final int REQUEST_FAVORITE  = 0x02;
    private static final int REQUEST_FAV_RT    = 0x03;
    private static final int REQUEST_RT_QUOTE  = 0x04;
    private static final int REQUEST_FRT_QUOTE = 0x05;
    private static final int REQUEST_CHANGE    = 0x06;
    private static final int REQUEST_QUOTE     = 0x07;

    private static final int BUTTON_SHOW_DURATION = 260;

    private static final String[] NUISANCES = {
            "ShootingStar",
            "TheWorld",
            "Biyon",
            "MoonStrike",
            "NightFox"
    };

    private PreformedStatus status = null;

    // URL引用のみを許可
    private boolean limitedQuote = false;

    private AlertDialog currentDialog = null;
    private ImageButton ibReply;
    private ImageButton ibFavorite;
    private ImageButton ibFavRt;
    private ImageButton ibRetweet;
    private ImageButton ibShare;
    private ImageButton ibQuote;
    private ImageButton ibAccount;
    private SharedPreferences sharedPreferences;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_status_main, container, false);
        Bundle b = getArguments();
        status = (PreformedStatus) b.getSerializable(StatusActivity.EXTRA_STATUS);

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());

        ibReply = (ImageButton) v.findViewById(R.id.ib_state_reply);
        ibReply.setOnClickListener(v1 -> {
            if (!(status.isMentionedTo(getUserRecord()) && status.getUserMentionEntities().length == 1) &&
                    status.getUserMentionEntities().length > 0 && sharedPreferences.getBoolean("pref_choose_reply_to", true)) {
                PopupMenu popupMenu = new PopupMenu(getActivity(), v1);
                popupMenu.inflate(R.menu.reply_to);
                popupMenu.setOnMenuItemClickListener(menuItem -> {
                    switch (menuItem.getItemId()) {
                        case R.id.action_reply_to_sender:
                            replyToSender();
                            return true;
                        case R.id.action_reply_to_all_mentions:
                            replyToAllMentions();
                            return true;
                    }
                    return false;
                });
                popupMenu.show();
            } else {
                replyToSender();
            }
        });
        ibReply.setOnLongClickListener(v1 -> {
            replyToAllMentions();
            return true;
        });

        ibRetweet = (ImageButton) v.findViewById(R.id.ib_state_retweet);
        ibRetweet.setOnClickListener(v1 -> {
            Intent intent = AsyncCommandService.createRetweet(getActivity().getApplicationContext(), status.getOriginStatus().getId(), getUserRecord());

            if (sharedPreferences.getBoolean("pref_dialog_rt", true)) {
                AlertDialog ad = new AlertDialog.Builder(getActivity())
                        .setTitle("確認")
                        .setMessage("リツイートしますか？")
                        .setPositiveButton("OK", (dialog, which) -> {
                            dialog.dismiss();
                            currentDialog = null;

                            getActivity().startService(intent);
                            closeAfterFavorite();
                        })
                        .setNegativeButton("キャンセル", (dialog, which) -> {
                            dialog.dismiss();
                            currentDialog = null;
                        })
                        .setOnCancelListener(dialog -> {
                            dialog.dismiss();
                            currentDialog = null;
                        })
                        .create();
                ad.show();
                currentDialog = ad;
            } else {
                getActivity().startService(intent);
                closeAfterFavorite();
            }
        });

        ibFavorite = (ImageButton) v.findViewById(R.id.ib_state_favorite);
        ibFavorite.setOnClickListener(new View.OnClickListener() {
            private void createFavorite(boolean withQuotes) {
                AuthUserRecord user = getUserRecord();

                Intent intent = AsyncCommandService.createFavorite(getActivity().getApplicationContext(), status.getOriginStatus().getId(), user);
                getActivity().startService(intent);

                if (withQuotes) {
                    for (Long id : status.getQuoteEntities()) {
                        Intent i = AsyncCommandService.createFavorite(getActivity().getApplicationContext(), id, user);
                        getActivity().startService(i);
                    }
                }
            }

            private boolean isNuisance(String source) {
                if (sharedPreferences.getBoolean("pref_guard_nuisance", true)) {
                    int l = NUISANCES.length;
                    for (int i = 0; i < l; i++) {
                        if (source.contains(NUISANCES[i])) return true;
                    }
                }
                return false;
            }

            private void nuisanceGuard() {
                AlertDialog ad = new AlertDialog.Builder(getActivity())
                        .setTitle("確認")
                        .setMessage("このツイートは" + status.getOriginSource() + "を使用して投稿されています。お気に入り登録してもよろしいですか？")
                        .setPositiveButton("ふぁぼる", (dialog, which) -> {
                            dialog.dismiss();
                            currentDialog = null;

                            createFavorite(false);
                            closeAfterFavorite();
                        })
                        .setNeutralButton("本文で検索", (dialog, which) -> {
                            dialog.dismiss();
                            currentDialog = null;
                            Intent intent = new Intent(getActivity(), MainActivity.class);
                            String query = String.format("\"%s\" -RT", status.getOriginStatus().getPlainText());
                            intent.putExtra(MainActivity.EXTRA_SEARCH_WORD, query);
                            startActivity(intent);
                        })
                        .setNegativeButton("キャンセル", (dialog, which) -> {
                            dialog.dismiss();
                            currentDialog = null;
                        })
                        .setOnCancelListener(dialog -> {
                            dialog.dismiss();
                            currentDialog = null;
                        })
                        .create();
                ad.show();
                currentDialog = ad;
            }

            private void doFavorite() {
                if (sharedPreferences.getBoolean("pref_dialog_fav", false)) {
                    AlertDialog ad = new AlertDialog.Builder(getActivity())
                            .setTitle("確認")
                            .setMessage("お気に入り登録しますか？")
                            .setPositiveButton("OK", (dialog, which) -> {
                                dialog.dismiss();
                                currentDialog = null;

                                if (isNuisance(status.getOriginSource())) {
                                    nuisanceGuard();
                                } else {
                                    createFavorite(false);
                                    closeAfterFavorite();
                                }
                            })
                            .setNegativeButton("キャンセル", (dialog, which) -> {
                                dialog.dismiss();
                                currentDialog = null;
                            })
                            .setOnCancelListener(dialog -> {
                                dialog.dismiss();
                                currentDialog = null;
                            })
                            .create();
                    ad.show();
                    currentDialog = ad;
                } else {
                    if (isNuisance(status.getOriginSource())) {
                        nuisanceGuard();
                    } else {
                        createFavorite(false);
                        closeAfterFavorite();
                    }
                }
            }

            private void doUnfavorite(final AuthUserRecord userRecord) {
                Intent intent = AsyncCommandService.destroyFavorite(getActivity().getApplicationContext(), status.getOriginStatus().getId(), userRecord);
                getActivity().startService(intent);
                closeAfterFavorite();
            }

            @AllArgsConstructor
            class Action {
                String label;
                Runnable onAction;
            }

            @Override
            public void onClick(View v) {
                final List<Action> items = new ArrayList<>();
                items.add(new Action("お気に入り登録", this::doFavorite));

                if (sharedPreferences.getBoolean("pref_fav_with_quotes", false) && !status.getQuoteEntities().isEmpty()) {
                    items.add(new Action("引用もまとめてお気に入り登録", () -> {
                        createFavorite(true);
                        closeAfterFavorite();
                    }));
                }

                final List<AuthUserRecord> faved = status.getFavoritedAccounts();
                for (AuthUserRecord userRecord : faved) {
                    items.add(new Action("解除: @" + userRecord.ScreenName, () -> doUnfavorite(userRecord)));
                }

                if (items.size() == 1) {
                    doFavorite();
                } else if (status.getFavoritedAccounts().size() == 1 && getTwitterService().getUsers().size() == 1) {
                    // 唯一ログインしているアカウントでふぁぼ済みの場合、ふぁぼ解は自明
                    doUnfavorite(status.getFavoritedAccounts().get(0));
                } else {
                    List<String> itemLabels = new ArrayList<>(items.size());
                    for (Action item : items) {
                        itemLabels.add(item.label);
                    }
                    AlertDialog ad = new AlertDialog.Builder(getActivity())
                            .setTitle("お気に入り/お気に入り解除")
                            .setItems(itemLabels.toArray(new String[itemLabels.size()]), (dialog, which) -> {
                                dialog.dismiss();
                                currentDialog = null;

                                items.get(which).onAction.run();
                            })
                            .setNegativeButton("キャンセル", (dialog, which) -> {
                                dialog.dismiss();
                                currentDialog = null;
                            })
                            .create();
                    ad.show();
                    currentDialog = ad;
                }
            }
        });

        ibFavRt = (ImageButton) v.findViewById(R.id.ib_state_favrt);
        ibFavRt.setOnClickListener(v1 -> {
            Intent intent = AsyncCommandService.createFavRT(getActivity().getApplicationContext(), status.getOriginStatus().getId(), getUserRecord());

            if (sharedPreferences.getBoolean("pref_dialog_favrt", true)) {
                AlertDialog ad = new AlertDialog.Builder(getActivity())
                        .setTitle("確認")
                        .setMessage("お気に入りに登録してRTしますか？")
                        .setPositiveButton("OK", (dialog, which) -> {
                            dialog.dismiss();
                            currentDialog = null;

                            getActivity().startService(intent);
                            closeAfterFavorite();
                        })
                        .setNegativeButton("キャンセル", (dialog, which) -> {
                            dialog.dismiss();
                            currentDialog = null;
                        })
                        .setOnCancelListener(dialog -> {
                            dialog.dismiss();
                            currentDialog = null;
                        })
                        .create();
                ad.show();
                currentDialog = ad;
            } else {
                getActivity().startService(intent);
                closeAfterFavorite();
            }
        });

        ibQuote = (ImageButton) v.findViewById(R.id.ib_state_quote);
        ibQuote.setOnClickListener(v1 -> {
            //引用制限時
            if (limitedQuote) {
                Intent intent = new Intent(getActivity(), TweetActivity.class);
                intent.putExtra(TweetActivity.EXTRA_USER, getUserRecord());
                intent.putExtra(TweetActivity.EXTRA_STATUS, status);
                intent.putExtra(TweetActivity.EXTRA_MODE, TweetActivity.MODE_QUOTE);
                intent.putExtra(TweetActivity.EXTRA_TEXT, " " + TwitterUtil.getTweetURL(status));
                startActivityForResult(intent, REQUEST_QUOTE);
                return;
            }

            int defaultQuote = Integer.parseInt(sharedPreferences.getString("pref_default_quote_2_0_1", "-1"));
            if (defaultQuote < 0) {
                showQuoteStyleSelector();
                return;
            }

            if (beginQuoteTweet(defaultQuote)) {
                String[] quoteStyles = getResources().getStringArray(R.array.pref_quote_entries);
                quoteStyles = Arrays.copyOfRange(quoteStyles, 1, quoteStyles.length);

                Toast toast = Toast.makeText(getActivity(), quoteStyles[defaultQuote], Toast.LENGTH_SHORT);
                toast.setGravity(Gravity.TOP | Gravity.CENTER, 0, 0);
                toast.show();
            }
        });
        ibQuote.setOnLongClickListener(v1 -> {
            //引用制限時
            if (limitedQuote) {
                Intent intent = new Intent(getActivity(), TweetActivity.class);
                intent.putExtra(TweetActivity.EXTRA_USER, getUserRecord());
                intent.putExtra(TweetActivity.EXTRA_STATUS, status);
                intent.putExtra(TweetActivity.EXTRA_MODE, TweetActivity.MODE_QUOTE);
                intent.putExtra(TweetActivity.EXTRA_TEXT, " " + TwitterUtil.getTweetURL(status));
                startActivityForResult(intent, REQUEST_QUOTE);
                return true;
            }

            showQuoteStyleSelector();
            return true;
        });

        ibShare = (ImageButton) v.findViewById(R.id.ib_state_share);
        ibShare.setOnClickListener(v1 -> {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (limitedQuote) {
                intent.putExtra(Intent.EXTRA_TEXT, TwitterUtil.getTweetURL(status));
            } else {
                intent.putExtra(Intent.EXTRA_TEXT, TwitterUtil.createSTOT(status));
            }
            startActivity(intent);
        });

        ibAccount = (ImageButton) v.findViewById(R.id.ib_state_account);
        if (getUserRecord().getSessionTemporary("OriginalProfileImageUrl") != null) {
            ImageLoaderTask.loadProfileIcon(getActivity(), ibAccount, (String) getUserRecord().getSessionTemporary("OriginalProfileImageUrl"));
        }
        ibAccount.setOnClickListener(v1 -> {
            Intent intent = new Intent(getActivity(), AccountChooserActivity.class);
            intent.putExtra(Intent.EXTRA_TITLE, "アカウント切り替え");
            startActivityForResult(intent, REQUEST_CHANGE);
        });

        return v;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        final float delta = getResources().getDimensionPixelSize(R.dimen.status_button_delta);

        ObjectAnimator.ofPropertyValuesHolder(ibReply,
                PropertyValuesHolder.ofFloat("translationX", 0f, -(delta / 2)),
                PropertyValuesHolder.ofFloat("translationY", 0f, -delta),
                PropertyValuesHolder.ofFloat("alpha", 0f, 1f))
                .setDuration(BUTTON_SHOW_DURATION)
                .start();
        ObjectAnimator.ofPropertyValuesHolder(ibRetweet,
                PropertyValuesHolder.ofFloat("translationX", 0f, delta / 2),
                PropertyValuesHolder.ofFloat("translationY", 0f, -delta),
                PropertyValuesHolder.ofFloat("alpha", 0f, 1f))
                .setDuration(BUTTON_SHOW_DURATION)
                .start();
        ObjectAnimator.ofPropertyValuesHolder(ibFavorite,
                PropertyValuesHolder.ofFloat("translationX", 0f, -delta),
                PropertyValuesHolder.ofFloat("alpha", 0f, 1f))
                .setDuration(BUTTON_SHOW_DURATION)
                .start();
        ObjectAnimator.ofPropertyValuesHolder(ibQuote,
                PropertyValuesHolder.ofFloat("translationX", 0f, delta),
                PropertyValuesHolder.ofFloat("alpha", 0f, 1f))
                .setDuration(BUTTON_SHOW_DURATION)
                .start();
        ObjectAnimator.ofPropertyValuesHolder(ibFavRt,
                PropertyValuesHolder.ofFloat("translationX", 0f, -(delta / 2)),
                PropertyValuesHolder.ofFloat("translationY", 0f, delta),
                PropertyValuesHolder.ofFloat("alpha", 0f, 1f))
                .setDuration(BUTTON_SHOW_DURATION)
                .start();
        ObjectAnimator.ofPropertyValuesHolder(ibShare,
                PropertyValuesHolder.ofFloat("translationX", 0f, (delta / 2)),
                PropertyValuesHolder.ofFloat("translationY", 0f, delta),
                PropertyValuesHolder.ofFloat("alpha", 0f, 1f))
                .setDuration(BUTTON_SHOW_DURATION)
                .start();
    }

    @Override
    public void onResume() {
        super.onResume();

        if (currentDialog != null) {
            currentDialog.show();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == REQUEST_REPLY || requestCode == REQUEST_QUOTE) {
                getActivity().finish();
            } else if (requestCode == REQUEST_RT_QUOTE) {
                TweetDraft draft = (TweetDraft) data.getSerializableExtra(TweetActivity.EXTRA_DRAFT);
                //これ、RT失敗してもツイートしちゃうんですよねえ
                getActivity().startService(PostService.newIntent(getActivity(), draft,
                        PostService.FLAG_RETWEET,
                        status.getId()));
                getActivity().finish();
            } else if (requestCode == REQUEST_FRT_QUOTE) {
                TweetDraft draft = (TweetDraft) data.getSerializableExtra(TweetActivity.EXTRA_DRAFT);
                //これ、FRT失敗してもツイートしちゃうんですよねえ
                getActivity().startService(PostService.newIntent(
                        getActivity(), draft,
                        PostService.FLAG_FAVORITE | PostService.FLAG_RETWEET,
                        status.getId()));
                getActivity().finish();
            } else if (requestCode == REQUEST_CHANGE) {
                setUserRecord((AuthUserRecord) data.getSerializableExtra(AccountChooserActivity.EXTRA_SELECTED_RECORD));
            } else {
                final ArrayList<AuthUserRecord> actionUsers =
                        (ArrayList<AuthUserRecord>) data.getSerializableExtra(AccountChooserActivity.EXTRA_SELECTED_RECORDS);
                List<Intent> intents = new ArrayList<>();
                int maskedCode = (requestCode & (REQUEST_FAVORITE | REQUEST_RETWEET));
                switch (maskedCode) {
                    case REQUEST_FAV_RT:
                        for (AuthUserRecord actionUser : actionUsers) {
                            intents.add(AsyncCommandService.createFavRT(
                                    getActivity().getApplicationContext(),
                                    status.getOriginStatus().getId(), actionUser));
                        }
                        break;
                    case REQUEST_FAVORITE:
                        for (AuthUserRecord actionUser : actionUsers) {
                            intents.add(AsyncCommandService.createFavorite(
                                    getActivity().getApplicationContext(),
                                    status.getOriginStatus().getId(), actionUser));
                        }
                        break;
                    case REQUEST_RETWEET:
                        for (AuthUserRecord actionUser : actionUsers) {
                            intents.add(AsyncCommandService.createRetweet(
                                    getActivity().getApplicationContext(),
                                    status.getOriginStatus().getId(), actionUser));
                        }
                        break;
                }
                for (Intent intent : intents) {
                    getActivity().startService(intent);
                }
            }
        }
    }

    @Override
    public void onUserChanged(AuthUserRecord userRecord) {
        loadProfileImage();
    }

    @Override
    public void onServiceConnected() {
        if (status.getOriginStatus().getUser().isProtected()) {
            //鍵postの場合
            ibRetweet.setEnabled(false);
            ibFavRt.setEnabled(false);
            limitedQuote = true;
        } else {
            limitedQuote = false;
        }
        if (getTwitterService().isMyTweet(status, true) != null) {
            //自分のツイートの場合
            ibRetweet.setEnabled(true);
            if (sharedPreferences.getBoolean("pref_narcist", false)) {
                ibFavorite.setEnabled(true);
                ibFavRt.setEnabled(true);
            } else {
                ibFavorite.setEnabled(false);
                ibFavRt.setEnabled(false);
            }
        }
        loadProfileImage();
    }

    @Override
    public void onServiceDisconnected() {}

    @Nullable
    private PreformedStatus getStatus() {
        if (getActivity() instanceof StatusUI) {
            return ((StatusUI) getActivity()).getStatus();
        }
        return null;
    }

    @Nullable
    private AuthUserRecord getUserRecord() {
        if (getActivity() instanceof StatusUI) {
            return ((StatusUI) getActivity()).getUserRecord();
        }
        return null;
    }

    private void setUserRecord(AuthUserRecord userRecord) {
        if (getActivity() instanceof StatusUI) {
            ((StatusUI) getActivity()).setUserRecord(userRecord);
        }
    }

    private void loadProfileImage() {
        final AuthUserRecord user = getUserRecord();
        if (user == null) {
            return;
        }

        if (user.getSessionTemporary("OriginalProfileImageUrl") != null) {
            ImageLoaderTask.loadProfileIcon(getActivity(), ibAccount, (String) user.getSessionTemporary("OriginalProfileImageUrl"));
        } else {
            TwitterService service = getTwitterService();
            if (service == null) {
                Log.d(StatusMainFragment.class.getSimpleName(), "loadProfileImage: missing service.");
                return;
            }
            Twitter twitter = service.getTwitterOrPrimary(user);
            if (twitter == null) {
                Log.d(StatusMainFragment.class.getSimpleName(), "loadProfileImage: missing twitter instance.");
                return;
            }

            new ThrowableTwitterAsyncTask<Long, String>(this) {
                @Override
                protected ThrowableResult<String> doInBackground(Long... params) {
                    try {
                        String url = twitter.showUser(params[0]).getOriginalProfileImageURLHttps();
                        return new ThrowableResult<>(url);
                    } catch (TwitterException e) {
                        e.printStackTrace();
                        return new ThrowableResult<>(e);
                    }
                }

                @Override
                protected void onPostExecute(ThrowableResult<String> result) {
                    super.onPostExecute(result);
                    if (!result.isException() && !isCancelled()) {
                        user.putSessionTemporary("OriginalProfileImageUrl", result.getResult());
                        ImageLoaderTask.loadProfileIcon(getActivity(), ibAccount, result.getResult());
                    }
                }

                @Override
                protected void showToast(String message) {
                    if (getActivity() != null && getActivity().getApplicationContext() != null) {
                        Toast.makeText(getActivity().getApplicationContext(), message, Toast.LENGTH_SHORT).show();
                    }
                }
            }.executeParallel(user.NumericId);
        }
    }

    private void closeAfterFavorite() {
        if (sharedPreferences.getBoolean("pref_close_after_fav", true)) {
            getActivity().finish();
        }
    }

    private void replyToSender() {
        Intent intent = new Intent(getActivity(), TweetActivity.class);
        intent.putExtra(TweetActivity.EXTRA_USER, getUserRecord());
        intent.putExtra(TweetActivity.EXTRA_STATUS, ((status.isRetweet()) ? status.getRetweetedStatus() : status));
        intent.putExtra(TweetActivity.EXTRA_MODE, TweetActivity.MODE_REPLY);
        intent.putExtra(TweetActivity.EXTRA_TEXT, "@" + status.getOriginStatus().getUser().getScreenName() + " ");
        startActivityForResult(intent, REQUEST_REPLY);
    }

    private void replyToAllMentions() {
        AuthUserRecord user = getUserRecord();

        Intent intent = new Intent(getActivity(), TweetActivity.class);
        intent.putExtra(TweetActivity.EXTRA_USER, user);
        intent.putExtra(TweetActivity.EXTRA_STATUS, ((status.isRetweet()) ? status.getRetweetedStatus() : status));
        intent.putExtra(TweetActivity.EXTRA_MODE, TweetActivity.MODE_REPLY);
        {
            StringBuilder ids = new StringBuilder();
            ids.append("@").append(status.getOriginStatus().getUser().getScreenName()).append(" ");
            for (UserMentionEntity entity : status.getUserMentionEntities()) {
                if (!ids.toString().contains("@" + entity.getScreenName())
                        && !entity.getScreenName().equals(user.ScreenName)) {
                    ids.append("@");
                    ids.append(entity.getScreenName());
                    ids.append(" ");
                }
            }

            intent.putExtra(TweetActivity.EXTRA_TEXT, ids.toString());
        }
        startActivityForResult(intent, REQUEST_REPLY);
    }

    private void showQuoteStyleSelector() {
        String[] quoteStyles = getResources().getStringArray(R.array.pref_quote_entries);
        quoteStyles = Arrays.copyOfRange(quoteStyles, 1, quoteStyles.length);

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle("引用形式を選択");
        builder.setNegativeButton("キャンセル", (dialog, which) -> {
            dialog.dismiss();
            currentDialog = null;
        });
        builder.setOnCancelListener(dialog -> {
            dialog.dismiss();
            currentDialog = null;
        });
        builder.setItems(
                quoteStyles,
                (dialog, which) -> {
                    dialog.dismiss();
                    currentDialog = null;

                    beginQuoteTweet(which);
                });
        AlertDialog ad = builder.create();
        ad.show();
        currentDialog = ad;
    }

    private boolean beginQuoteTweet(int which) {
        Intent intent = new Intent(getActivity(), TweetActivity.class);
        intent.putExtra(TweetActivity.EXTRA_USER, getUserRecord());
        intent.putExtra(TweetActivity.EXTRA_STATUS, status);
        if (which < 3) {
            intent.putExtra(TweetActivity.EXTRA_MODE, TweetActivity.MODE_QUOTE);
            switch (which) {
                case 0:
                    intent.putExtra(TweetActivity.EXTRA_TEXT, TwitterUtil.createQuotedRT(status));
                    break;
                case 1:
                    intent.putExtra(TweetActivity.EXTRA_TEXT, TwitterUtil.createQT(status));
                    break;
                case 2:
                    intent.putExtra(TweetActivity.EXTRA_TEXT, " " + TwitterUtil.getTweetURL(status));
                    break;
            }
            startActivityForResult(intent, REQUEST_QUOTE);
        } else {
            int request = -1;
            switch (which) {
                case 3:
                    if (!ibRetweet.isEnabled()) {
                        Toast.makeText(getActivity(), "RTできないツイートです。\nこの操作を行うことができません。", Toast.LENGTH_SHORT).show();
                        return false;
                    }
                    request = REQUEST_RT_QUOTE;
                    break;
                case 4:
                    if (!ibFavRt.isEnabled()) {
                        Toast.makeText(getActivity(), "FavRTできないツイートです。\nこの操作を行うことができません。", Toast.LENGTH_SHORT).show();
                        return false;
                    }
                    request = REQUEST_FRT_QUOTE;
                    break;
            }
            if (request > -1) {
                intent.putExtra(TweetActivity.EXTRA_MODE, TweetActivity.MODE_COMPOSE);
                intent.putExtra(TweetActivity.EXTRA_TEXT, sharedPreferences.getString("pref_quote_comment_footer", " ＞RT"));
                startActivityForResult(intent, request);
            }
        }
        return true;
    }
}
