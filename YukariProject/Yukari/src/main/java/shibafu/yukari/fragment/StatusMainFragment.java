package shibafu.yukari.fragment;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import shibafu.yukari.R;
import shibafu.yukari.activity.AccountChooserActivity;
import shibafu.yukari.activity.MainActivity;
import shibafu.yukari.activity.StatusActivity;
import shibafu.yukari.activity.TweetActivity;
import shibafu.yukari.common.TweetDraft;
import shibafu.yukari.common.async.ParallelAsyncTask;
import shibafu.yukari.common.async.SimpleAsyncTask;
import shibafu.yukari.fragment.base.TwitterFragment;
import shibafu.yukari.service.PostService;
import shibafu.yukari.twitter.AuthUserRecord;
import shibafu.yukari.twitter.TwitterUtil;
import shibafu.yukari.twitter.statusimpl.PreformedStatus;
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

    private static final String[] NUISANCES = {
            "ShootingStar",
            "TheWorld",
            "Biyon",
            "MoonStrike",
            "NightFox"
    };

    private PreformedStatus status = null;
    private AuthUserRecord user = null;

    private AlertDialog currentDialog = null;
    private ImageButton ibFavorite;
    private ImageButton ibFavRt;
    private ImageButton ibRetweet;
    private ImageButton ibShare;
    private ImageButton ibQuote;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_status_main, container, false);
        Bundle b = getArguments();
        status = (PreformedStatus) b.getSerializable(StatusActivity.EXTRA_STATUS);
        user = (AuthUserRecord) b.getSerializable(StatusActivity.EXTRA_USER);

        ImageButton ibReply = (ImageButton) v.findViewById(R.id.ib_state_reply);
        ibReply.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getActivity(), TweetActivity.class);
                intent.putExtra(TweetActivity.EXTRA_USER, user);
                intent.putExtra(TweetActivity.EXTRA_STATUS, ((status.isRetweet())?status.getRetweetedStatus() : status));
                intent.putExtra(TweetActivity.EXTRA_MODE, TweetActivity.MODE_REPLY);
                intent.putExtra(TweetActivity.EXTRA_TEXT, "@" +
                        ((status.isRetweet())?status.getRetweetedStatus().getUser().getScreenName()
                                : status.getUser().getScreenName()) + " ");
                startActivityForResult(intent, REQUEST_REPLY);
            }
        });
        ibReply.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Intent intent = new Intent(getActivity(), TweetActivity.class);
                intent.putExtra(TweetActivity.EXTRA_USER, user);
                intent.putExtra(TweetActivity.EXTRA_STATUS, ((status.isRetweet())?status.getRetweetedStatus() : status));
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
                return true;
            }
        });

        ibRetweet = (ImageButton) v.findViewById(R.id.ib_state_retweet);
        ibRetweet.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog ad = new AlertDialog.Builder(getActivity())
                        .setTitle("確認")
                        .setMessage("リツイートしますか？")
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                                currentDialog = null;

                                AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
                                    @Override
                                    protected Void doInBackground(Void... params) {
                                        getTwitterService().retweetStatus(user, (status.isRetweet()) ? status.getRetweetedStatus().getId() : status.getId());
                                        return null;
                                    }
                                };
                                task.execute();
                                getActivity().finish();
                            }
                        })
                        .setNegativeButton("キャンセル", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                                currentDialog = null;
                            }
                        })
                        .setOnCancelListener(new DialogInterface.OnCancelListener() {
                            @Override
                            public void onCancel(DialogInterface dialog) {
                                dialog.dismiss();
                                currentDialog = null;
                            }
                        })
                        .create();
                ad.show();
                currentDialog = ad;
            }
        });
        ibRetweet.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Intent intent = new Intent(getActivity(), AccountChooserActivity.class);
                intent.putExtra(AccountChooserActivity.EXTRA_MULTIPLE_CHOOSE, true);
                intent.putExtra(Intent.EXTRA_TITLE, "マルチアカウントRT");
                startActivityForResult(intent, REQUEST_RETWEET);
                Toast.makeText(getActivity(),
                        "アカウントを選択し、戻るキーで確定します。\nなにも選択していない場合キャンセルされます。",
                        Toast.LENGTH_LONG).show();
                return true;
            }
        });

        ibFavorite = (ImageButton) v.findViewById(R.id.ib_state_favorite);
        ibFavorite.setOnClickListener(new View.OnClickListener() {
            private SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getActivity());

            private boolean isNuisance(String source) {
                int l = NUISANCES.length;
                for (int i = 0; i < l; i++) {
                    if (source.contains(NUISANCES[i])) return true;
                }
                return false;
            }

            private void doFavorite() {
                final AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
                    @Override
                    protected Void doInBackground(Void... params) {
                        getTwitterService().createFavorite(user, (status.isRetweet()) ? status.getRetweetedStatus().getId() : status.getId());
                        return null;
                    }
                };
                String source = status.getSource();
                if (status.isRetweet()) {
                    source = status.getRetweetedStatus().getSource();
                }
                if (pref.getBoolean("pref_guard_nuisance", true) && isNuisance(source)) {
                    AlertDialog ad = new AlertDialog.Builder(getActivity())
                            .setTitle("確認")
                            .setMessage("このツイートは" + source + "を使用して投稿されています。お気に入り登録してもよろしいですか？")
                            .setIcon(android.R.drawable.ic_dialog_alert)
                            .setPositiveButton("ふぁぼる", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                    currentDialog = null;
                                    task.execute();
                                    getActivity().finish();
                                }
                            })
                            .setNeutralButton("本文で検索", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                    currentDialog = null;
                                    Intent intent = new Intent(getActivity(), MainActivity.class);
                                    String query = String.format("\"%s\" -RT", status.isRetweet() ? status.getRetweetedStatus().getPlainText() : status.getPlainText());
                                    intent.putExtra(MainActivity.EXTRA_SEARCH_WORD, query);
                                    startActivity(intent);
                                }
                            })
                            .setNegativeButton("キャンセル", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                    currentDialog = null;
                                }
                            })
                            .setOnCancelListener(new DialogInterface.OnCancelListener() {
                                @Override
                                public void onCancel(DialogInterface dialog) {
                                    dialog.dismiss();
                                    currentDialog = null;
                                }
                            })
                            .create();
                    ad.show();
                    currentDialog = ad;
                } else {
                    task.execute();
                    getActivity().finish();
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
                getActivity().finish();
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
                                .setItems(items.toArray(new String[items.size()]), new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();
                                        currentDialog = null;
                                        if (which == 0) doFavorite();
                                        else doUnfavorite(faved.get(which - 1));
                                    }
                                })
                                .setNegativeButton("キャンセル", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();
                                        currentDialog = null;
                                    }
                                })
                                .create();
                        ad.show();
                        currentDialog = ad;
                    }
                }
                else doFavorite();
            }
        });
        ibFavorite.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Intent intent = new Intent(getActivity(), AccountChooserActivity.class);
                intent.putExtra(AccountChooserActivity.EXTRA_MULTIPLE_CHOOSE, true);
                intent.putExtra(Intent.EXTRA_TITLE, "マルチアカウントFav");
                startActivityForResult(intent, REQUEST_FAVORITE);
                Toast.makeText(getActivity(),
                        "アカウントを選択し、戻るキーで確定します。\nなにも選択していない場合キャンセルされます。",
                        Toast.LENGTH_LONG).show();
                return true;
            }
        });

        ibFavRt = (ImageButton) v.findViewById(R.id.ib_state_favrt);
        ibFavRt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog ad = new AlertDialog.Builder(getActivity())
                        .setTitle("確認")
                        .setMessage("お気に入りに登録してRTしますか？")
                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                                currentDialog = null;

                                AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
                                    @Override
                                    protected Void doInBackground(Void... params) {
                                        getTwitterService().retweetStatus(user, (status.isRetweet()) ? status.getRetweetedStatus().getId() : status.getId());
                                        getTwitterService().createFavorite(user, (status.isRetweet()) ? status.getRetweetedStatus().getId() : status.getId());
                                        return null;
                                    }
                                };
                                task.execute();
                                getActivity().finish();
                            }
                        })
                        .setNegativeButton("キャンセル", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                                currentDialog = null;
                            }
                        })
                        .setOnCancelListener(new DialogInterface.OnCancelListener() {
                            @Override
                            public void onCancel(DialogInterface dialog) {
                                dialog.dismiss();
                                currentDialog = null;
                            }
                        })
                        .create();
                ad.show();
                currentDialog = ad;
            }
        });
        ibFavRt.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Intent intent = new Intent(getActivity(), AccountChooserActivity.class);
                intent.putExtra(AccountChooserActivity.EXTRA_MULTIPLE_CHOOSE, true);
                intent.putExtra(Intent.EXTRA_TITLE, "マルチアカウントFav&RT");
                startActivityForResult(intent, REQUEST_FAV_RT);
                Toast.makeText(getActivity(),
                        "アカウントを選択し、戻るキーで確定します。\nなにも選択していない場合キャンセルされます。",
                        Toast.LENGTH_LONG).show();
                return true;
            }
        });

        ibQuote = (ImageButton) v.findViewById(R.id.ib_state_quote);
        ibQuote.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                builder.setTitle("引用形式を選択");
                builder.setNeutralButton("キャンセル", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        currentDialog = null;
                    }
                });
                builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        dialog.dismiss();
                        currentDialog = null;
                    }
                });
                builder.setItems(
                        new String[]{
                                "非公式RT ( RT @id: ... )",
                                "QT ( QT @id: ... )",
                                "公式アプリ風 ( \"@id: ...\" )",
                                "URLのみ ( http://... )",
                                "RTしてから言及する ( ...＞RT )",
                                "FavRTしてから言及する ( ...＞RT )"},
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                                currentDialog = null;

                                Intent intent = new Intent(getActivity(), TweetActivity.class);
                                intent.putExtra(TweetActivity.EXTRA_USER, user);
                                intent.putExtra(TweetActivity.EXTRA_STATUS, status);
                                if (which < 4) {
                                    intent.putExtra(TweetActivity.EXTRA_MODE, TweetActivity.MODE_QUOTE);
                                    switch (which) {
                                        case 0:
                                            intent.putExtra(TweetActivity.EXTRA_TEXT, TwitterUtil.createQuotedRT(status));
                                            break;
                                        case 1:
                                            intent.putExtra(TweetActivity.EXTRA_TEXT, TwitterUtil.createQT(status));
                                            break;
                                        case 2:
                                            intent.putExtra(TweetActivity.EXTRA_TEXT, TwitterUtil.createQuote(status));
                                            break;
                                        case 3:
                                            intent.putExtra(TweetActivity.EXTRA_TEXT, " " + TwitterUtil.getTweetURL(status));
                                            break;
                                    }
                                    startActivity(intent);
                                } else {
                                    int request = -1;
                                    switch (which) {
                                        case 4:
                                            if (!ibRetweet.isEnabled()) {
                                                Toast.makeText(getActivity(), "RTできないツイートです。\nこの操作を行うことができません。", Toast.LENGTH_SHORT).show();
                                                return;
                                            }
                                            request = REQUEST_RT_QUOTE;
                                            break;
                                        case 5:
                                            if (!ibFavRt.isEnabled()) {
                                                Toast.makeText(getActivity(), "FavRTできないツイートです。\nこの操作を行うことができません。", Toast.LENGTH_SHORT).show();
                                                return;
                                            }
                                            request = REQUEST_FRT_QUOTE;
                                            break;
                                    }
                                    if (request > -1) {
                                        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getActivity());
                                        intent.putExtra(TweetActivity.EXTRA_MODE, TweetActivity.MODE_COMPOSE);
                                        intent.putExtra(TweetActivity.EXTRA_TEXT, sp.getString("pref_quote_comment_footer", " ＞RT"));
                                        startActivityForResult(intent, request);
                                    }
                                }
                            }
                        });
                AlertDialog ad = builder.create();
                ad.show();
                currentDialog = ad;
            }
        });

        ibShare = (ImageButton) v.findViewById(R.id.ib_state_share);
        ibShare.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String stot = TwitterUtil.createSTOT(status);
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("text/plain");
                intent.putExtra(Intent.EXTRA_TEXT, stot);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }
        });

        return v;
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
            if (requestCode == REQUEST_REPLY) {
                getActivity().finish();
            }
            else if (requestCode == REQUEST_RT_QUOTE) {
                TweetDraft draft = (TweetDraft) data.getSerializableExtra(TweetActivity.EXTRA_DRAFT);
                new ParallelAsyncTask<TweetDraft, Void, Void>() {
                    @Override
                    protected Void doInBackground(TweetDraft... params) {
                        //これ、RT失敗してもツイートしちゃうんですよねえ
                        getActivity().startService(PostService.newIntent(getActivity(), params[0],
                                PostService.FLAG_RETWEET,
                                status.getId()));
                        return null;
                    }
                }.executeParallel(draft);
            }
            else if (requestCode == REQUEST_FRT_QUOTE) {
                TweetDraft draft = (TweetDraft) data.getSerializableExtra(TweetActivity.EXTRA_DRAFT);
                new ParallelAsyncTask<TweetDraft, Void, Void>() {
                    @Override
                    protected Void doInBackground(TweetDraft... params) {
                        //これ、FRT失敗してもツイートしちゃうんですよねえ
                        getActivity().startService(PostService.newIntent(
                                getActivity(), params[0],
                                PostService.FLAG_FAVORITE | PostService.FLAG_RETWEET,
                                status.getId()));
                        return null;
                    }
                }.executeParallel(draft);
            }
            else {
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

    @Override
    public void onServiceConnected() {
        if (getTwitterService().isMyTweet(status) != null && !status.isRetweet() &&
                !PreferenceManager.getDefaultSharedPreferences(getActivity()).getBoolean("pref_narcist", false)) {
            //自分のツイートの場合
            ibFavorite.setEnabled(false);
            ibFavRt.setEnabled(false);
        }
        if ((!status.isRetweet() && status.getUser().isProtected()) ||
                (status.isRetweet() && status.getRetweetedStatus().getUser().isProtected())) {
            //鍵postの場合
            ibRetweet.setEnabled(false);
            ibFavRt.setEnabled(false);
            ibShare.setEnabled(false);
            ibQuote.setEnabled(false);
        }
    }

    @Override
    public void onServiceDisconnected() {

    }
}
