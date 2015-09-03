package shibafu.yukari.fragment;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v7.widget.PopupMenu;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.Toast;

import com.nineoldandroids.animation.ObjectAnimator;
import com.nineoldandroids.animation.PropertyValuesHolder;

import java.util.ArrayList;
import java.util.List;

import shibafu.yukari.R;
import shibafu.yukari.activity.AccountChooserActivity;
import shibafu.yukari.activity.MainActivity;
import shibafu.yukari.activity.StatusActivity;
import shibafu.yukari.activity.TweetActivity;
import shibafu.yukari.common.TweetDraft;
import shibafu.yukari.common.async.SimpleAsyncTask;
import shibafu.yukari.common.async.ThrowableTwitterAsyncTask;
import shibafu.yukari.common.bitmapcache.ImageLoaderTask;
import shibafu.yukari.fragment.base.TwitterFragment;
import shibafu.yukari.service.PostService;
import shibafu.yukari.twitter.AuthUserRecord;
import shibafu.yukari.twitter.TwitterUtil;
import shibafu.yukari.twitter.statusimpl.PreformedStatus;
import twitter4j.TwitterException;
import twitter4j.UserMentionEntity;

/**
 * Created by Shibafu on 13/08/02.
 */
public class StatusMainFragment extends TwitterFragment{

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
    private AuthUserRecord user = null;

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
        user = (AuthUserRecord) b.getSerializable(StatusActivity.EXTRA_USER);

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());

        class LocalFunction {
            void closeAfterFavorite() {
                if (sharedPreferences.getBoolean("pref_close_after_fav", true)) {
                    getActivity().finish();
                }
            }

            void replyToSender() {
                Intent intent = new Intent(getActivity(), TweetActivity.class);
                intent.putExtra(TweetActivity.EXTRA_USER, user);
                intent.putExtra(TweetActivity.EXTRA_STATUS, ((status.isRetweet()) ? status.getRetweetedStatus() : status));
                intent.putExtra(TweetActivity.EXTRA_MODE, TweetActivity.MODE_REPLY);
                intent.putExtra(TweetActivity.EXTRA_TEXT, "@" +
                        ((status.isRetweet()) ? status.getRetweetedStatus().getUser().getScreenName()
                                : status.getUser().getScreenName()) + " ");
                startActivityForResult(intent, REQUEST_REPLY);
            }

            void replyToAllMentions() {
                Intent intent = new Intent(getActivity(), TweetActivity.class);
                intent.putExtra(TweetActivity.EXTRA_USER, user);
                intent.putExtra(TweetActivity.EXTRA_STATUS, ((status.isRetweet()) ? status.getRetweetedStatus() : status));
                intent.putExtra(TweetActivity.EXTRA_MODE, TweetActivity.MODE_REPLY);
                {
                    StringBuilder ids = new StringBuilder(
                            String.format("@%s ",
                                    ((status.isRetweet()) ?
                                            status.getRetweetedStatus().getUser().getScreenName()
                                            : status.getUser().getScreenName())));
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

            boolean beginQuoteTweet(int which) {
                Intent intent = new Intent(getActivity(), TweetActivity.class);
                intent.putExtra(TweetActivity.EXTRA_USER, user);
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
        final LocalFunction local = new LocalFunction();

        ibReply = (ImageButton) v.findViewById(R.id.ib_state_reply);
        ibReply.setOnClickListener(v1 -> {
            if (!(status.isMentionedTo(user) && status.getUserMentionEntities().length == 1) &&
                    status.getUserMentionEntities().length > 0 && sharedPreferences.getBoolean("pref_choose_reply_to", true)) {
                PopupMenu popupMenu = new PopupMenu(getActivity(), v1);
                popupMenu.inflate(R.menu.reply_to);
                popupMenu.setOnMenuItemClickListener(menuItem -> {
                    switch (menuItem.getItemId()) {
                        case R.id.action_reply_to_sender:
                            local.replyToSender();
                            return true;
                        case R.id.action_reply_to_all_mentions:
                            local.replyToAllMentions();
                            return true;
                    }
                    return false;
                });
                popupMenu.show();
            } else {
                local.replyToSender();
            }
        });
        ibReply.setOnLongClickListener(v1 -> {
            local.replyToAllMentions();
            return true;
        });

        ibRetweet = (ImageButton) v.findViewById(R.id.ib_state_retweet);
        ibRetweet.setOnClickListener(v1 -> {
            final AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... params) {
                    getTwitterService().retweetStatus(user, (status.isRetweet()) ? status.getRetweetedStatus().getId() : status.getId());
                    return null;
                }
            };
            if (sharedPreferences.getBoolean("pref_dialog_rt", true)) {
                AlertDialog ad = new AlertDialog.Builder(getActivity())
                        .setTitle("確認")
                        .setMessage("リツイートしますか？")
                        .setPositiveButton("OK", (dialog, which) -> {
                            dialog.dismiss();
                            currentDialog = null;

                            task.execute();
                            local.closeAfterFavorite();
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
                task.execute();
                local.closeAfterFavorite();
            }
        });
        ibRetweet.setOnLongClickListener(v1 -> {
            Intent intent = new Intent(getActivity(), AccountChooserActivity.class);
            intent.putExtra(AccountChooserActivity.EXTRA_MULTIPLE_CHOOSE, true);
            intent.putExtra(Intent.EXTRA_TITLE, "マルチアカウントRT");
            startActivityForResult(intent, REQUEST_RETWEET);
            Toast.makeText(getActivity(),
                    "アカウントを選択し、戻るキーで確定します。\nなにも選択していない場合キャンセルされます。",
                    Toast.LENGTH_LONG).show();
            return true;
        });

        ibFavorite = (ImageButton) v.findViewById(R.id.ib_state_favorite);
        ibFavorite.setOnClickListener(new View.OnClickListener() {
            private final AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... params) {
                    getTwitterService().createFavorite(user, (status.isRetweet()) ? status.getRetweetedStatus().getId() : status.getId());
                    return null;
                }
            };

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
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setPositiveButton("ふぁぼる", (dialog, which) -> {
                            dialog.dismiss();
                            currentDialog = null;

                            task.execute();
                            local.closeAfterFavorite();
                        })
                        .setNeutralButton("本文で検索", (dialog, which) -> {
                            dialog.dismiss();
                            currentDialog = null;
                            Intent intent = new Intent(getActivity(), MainActivity.class);
                            String query = String.format("\"%s\" -RT", status.isRetweet() ? status.getRetweetedStatus().getPlainText() : status.getPlainText());
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
                                    task.execute();
                                    local.closeAfterFavorite();
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
                        task.execute();
                        local.closeAfterFavorite();
                    }
                }
            }

            private void doUnfavorite(final AuthUserRecord userRecord) {
                new SimpleAsyncTask() {
                    @Override
                    protected Void doInBackground(Void... params) {
                        getTwitterService().destroyFavorite(userRecord,
                                (status.isRetweet()) ? status.getRetweetedStatus().getId() : status.getId());
                        return null;
                    }
                }.executeParallel();
                local.closeAfterFavorite();
            }

            @Override
            public void onClick(View v) {
                if (status.isFavoritedSomeone()) {
                    final List<AuthUserRecord> faved = status.getFavoritedAccounts();
                    if (faved.size() == 1 && getTwitterService().getUsers().size() == 1) {
                        doUnfavorite(faved.get(0));
                    }
                    else {
                        List<String> items = new ArrayList<>();
                        items.add("お気に入り登録");
                        for (AuthUserRecord userRecord : faved) {
                            items.add("解除: @" + userRecord.ScreenName);
                        }
                        AlertDialog ad = new AlertDialog.Builder(getActivity())
                                .setTitle("お気に入り/お気に入り解除")
                                .setItems(items.toArray(new String[items.size()]), (dialog, which) -> {
                                    dialog.dismiss();
                                    currentDialog = null;
                                    if (which == 0) doFavorite();
                                    else doUnfavorite(faved.get(which - 1));
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
                else doFavorite();
            }
        });
        ibFavorite.setOnLongClickListener(v1 -> {
            Intent intent = new Intent(getActivity(), AccountChooserActivity.class);
            intent.putExtra(AccountChooserActivity.EXTRA_MULTIPLE_CHOOSE, true);
            intent.putExtra(Intent.EXTRA_TITLE, "マルチアカウントFav");
            startActivityForResult(intent, REQUEST_FAVORITE);
            Toast.makeText(getActivity(),
                    "アカウントを選択し、戻るキーで確定します。\nなにも選択していない場合キャンセルされます。",
                    Toast.LENGTH_LONG).show();
            return true;
        });

        ibFavRt = (ImageButton) v.findViewById(R.id.ib_state_favrt);
        ibFavRt.setOnClickListener(v1 -> {
            final AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... params) {
                    getTwitterService().retweetStatus(user, (status.isRetweet()) ? status.getRetweetedStatus().getId() : status.getId());
                    getTwitterService().createFavorite(user, (status.isRetweet()) ? status.getRetweetedStatus().getId() : status.getId());
                    return null;
                }
            };
            if (sharedPreferences.getBoolean("pref_dialog_favrt", true)) {
                AlertDialog ad = new AlertDialog.Builder(getActivity())
                        .setTitle("確認")
                        .setMessage("お気に入りに登録してRTしますか？")
                        .setPositiveButton("OK", (dialog, which) -> {
                            dialog.dismiss();
                            currentDialog = null;

                            task.execute();
                            local.closeAfterFavorite();
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
                task.execute();
                local.closeAfterFavorite();
            }
        });
        ibFavRt.setOnLongClickListener(v1 -> {
            Intent intent = new Intent(getActivity(), AccountChooserActivity.class);
            intent.putExtra(AccountChooserActivity.EXTRA_MULTIPLE_CHOOSE, true);
            intent.putExtra(Intent.EXTRA_TITLE, "マルチアカウントFav&RT");
            startActivityForResult(intent, REQUEST_FAV_RT);
            Toast.makeText(getActivity(),
                    "アカウントを選択し、戻るキーで確定します。\nなにも選択していない場合キャンセルされます。",
                    Toast.LENGTH_LONG).show();
            return true;
        });

        ibQuote = (ImageButton) v.findViewById(R.id.ib_state_quote);
        ibQuote.setOnClickListener(v1 -> {
            //引用制限時
            if (limitedQuote) {
                Intent intent = new Intent(getActivity(), TweetActivity.class);
                intent.putExtra(TweetActivity.EXTRA_USER, user);
                intent.putExtra(TweetActivity.EXTRA_STATUS, status);
                intent.putExtra(TweetActivity.EXTRA_MODE, TweetActivity.MODE_QUOTE);
                intent.putExtra(TweetActivity.EXTRA_TEXT, " " + TwitterUtil.getTweetURL(status));
                startActivityForResult(intent, REQUEST_QUOTE);
                return;
            }

            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle("引用形式を選択");
            builder.setNeutralButton("キャンセル", (dialog, which) -> {
                dialog.dismiss();
                currentDialog = null;
            });
            builder.setOnCancelListener(dialog -> {
                dialog.dismiss();
                currentDialog = null;
            });
            builder.setItems(
                    R.array.pref_quote_entries,
                    (dialog, which) -> {
                        dialog.dismiss();
                        currentDialog = null;

                        local.beginQuoteTweet(which);
                    });
            AlertDialog ad = builder.create();
            ad.show();
            currentDialog = ad;
        });
        ibQuote.setOnLongClickListener(v1 -> {
            int defaultQuote = Integer.parseInt(sharedPreferences.getString("pref_default_quote", "2"));
            if (local.beginQuoteTweet(defaultQuote)) {
                Toast toast = Toast.makeText(getActivity(), getResources().getStringArray(R.array.pref_quote_entries)[defaultQuote], Toast.LENGTH_SHORT);
                toast.setGravity(Gravity.CENTER, 0, 0);
                toast.show();
            }
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
        if (user.getSessionTemporary("OriginalProfileImageUrl") != null) {
            ImageLoaderTask.loadProfileIcon(getActivity(), ibAccount, (String) user.getSessionTemporary("OriginalProfileImageUrl"));
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
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
                user = (AuthUserRecord) data.getSerializableExtra(AccountChooserActivity.EXTRA_SELECTED_RECORD);
                loadProfileImage();
            } else {
                final ArrayList<AuthUserRecord> actionUsers =
                        (ArrayList<AuthUserRecord>) data.getSerializableExtra(AccountChooserActivity.EXTRA_SELECTED_RECORDS);
                if ((requestCode & REQUEST_RETWEET) == REQUEST_RETWEET) {
                    new SimpleAsyncTask() {
                        @Override
                        protected Void doInBackground(Void... params) {
                            for (AuthUserRecord user : actionUsers) {
                                getTwitterService().retweetStatus(user, (status.isRetweet()) ? status.getRetweetedStatus().getId() : status.getId());
                            }
                            return null;
                        }
                    }.executeParallel();
                }
                if ((requestCode & REQUEST_FAVORITE) == REQUEST_FAVORITE) {
                    new SimpleAsyncTask() {
                        @Override
                        protected Void doInBackground(Void... params) {
                            for (AuthUserRecord user : actionUsers) {
                                getTwitterService().createFavorite(user, (status.isRetweet()) ? status.getRetweetedStatus().getId() : status.getId());
                            }
                            return null;
                        }
                    }.executeParallel();
                }
            }
        }
    }

    private void loadProfileImage() {
        if (user.getSessionTemporary("OriginalProfileImageUrl") != null) {
            ImageLoaderTask.loadProfileIcon(getActivity(), ibAccount, (String) user.getSessionTemporary("OriginalProfileImageUrl"));
        } else {
            new ThrowableTwitterAsyncTask<Long, String>(this) {
                @Override
                protected ThrowableResult<String> doInBackground(Long... params) {
                    try {
                        String url = getTwitterService().getTwitter().showUser(params[0]).getOriginalProfileImageURLHttps();
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

    @Override
    public void onServiceConnected() {
        if (getTwitterService().isMyTweet(status) != null && !status.isRetweet() &&
                !sharedPreferences.getBoolean("pref_narcist", false)) {
            //自分のツイートの場合
            ibFavorite.setEnabled(false);
            ibFavRt.setEnabled(false);
        }
        if ((!status.isRetweet() && status.getUser().isProtected()) ||
                (status.isRetweet() && status.getRetweetedStatus().getUser().isProtected())) {
            //鍵postの場合
            ibRetweet.setEnabled(false);
            ibFavRt.setEnabled(false);
            limitedQuote = true;
        } else {
            limitedQuote = false;
        }
        loadProfileImage();
    }

    @Override
    public void onServiceDisconnected() {

    }
}
