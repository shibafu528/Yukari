package shibafu.yukari.fragment;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

import shibafu.yukari.R;
import shibafu.yukari.activity.AccountChooserActivity;
import shibafu.yukari.activity.MainActivity;
import shibafu.yukari.activity.StatusActivity;
import shibafu.yukari.activity.TweetActivity;
import shibafu.yukari.common.async.SimpleAsyncTask;
import shibafu.yukari.common.TweetAdapterWrap;
import shibafu.yukari.service.TwitterService;
import shibafu.yukari.twitter.AuthUserRecord;
import shibafu.yukari.twitter.PreformedStatus;
import shibafu.yukari.twitter.TwitterUtil;

/**
 * Created by Shibafu on 13/08/02.
 */
public class StatusMainFragment extends Fragment{

    private static final int REQUEST_REPLY    = 0x00;
    private static final int REQUEST_RETWEET  = 0x01;
    private static final int REQUEST_FAVORITE = 0x02;
    private static final int REQUEST_FAV_RT   = 0x03;

    private PreformedStatus status = null;
    private AuthUserRecord user = null;

    private TwitterService service;
    private boolean serviceBound = false;

    private TweetAdapterWrap.ViewConverter viewConverter;

    private AlertDialog currentDialog = null;
    private ImageButton ibFavorite;
    private ImageButton ibFavRt;
    private ImageButton ibRetweet;
    private View tweetView;
    private ImageButton ibShare;
    private ImageButton ibQuote;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_status_main, container, false);
        Bundle b = getArguments();
        status = (PreformedStatus) b.getSerializable(StatusActivity.EXTRA_STATUS);
        user = (AuthUserRecord) b.getSerializable(StatusActivity.EXTRA_USER);
        tweetView = v.findViewById(R.id.status_tweet);

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
                                        service.retweetStatus(user, (status.isRetweet()) ? status.getRetweetedStatus().getId() : status.getId());
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
            @Override
            public void onClick(View v) {
                final AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
                    @Override
                    protected Void doInBackground(Void... params) {
                        service.createFavorite(user, (status.isRetweet()) ? status.getRetweetedStatus().getId() : status.getId());
                        return null;
                    }
                };
                String source = status.getSource();
                if (status.isRetweet()) {
                    source = status.getRetweetedStatus().getSource();
                }
                if (source.contains("ShootingStar") ||
                        source.contains("TheWorld") ||
                        source.contains("Biyon≡(　ε:)") ||
                        source.contains("MoonStrike"))
                {
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
                                    intent.putExtra(MainActivity.EXTRA_SEARCH_WORD, status.getPlainText());
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
                }
                else {
                    task.execute();
                    getActivity().finish();
                }
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
                                        service.retweetStatus(user, (status.isRetweet()) ? status.getRetweetedStatus().getId() : status.getId());
                                        service.createFavorite(user, (status.isRetweet()) ? status.getRetweetedStatus().getId() : status.getId());
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
                                "URLのみ ( http://... )"},
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                                currentDialog = null;

                                Intent intent = new Intent(getActivity(), TweetActivity.class);
                                intent.putExtra(TweetActivity.EXTRA_USER, user);
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

        TextView tvCounter = (TextView) v.findViewById(R.id.tv_state_counter);
        int retweeted = status.isRetweet()? status.getRetweetedStatus().getRetweetCount() : status.getRetweetCount();
        int faved = status.isRetweet()? status.getRetweetedStatus().getFavoriteCount() : status.getFavoriteCount();
        String countRT = retweeted + "RT";
        String countFav = faved + "Fav";
        if (retweeted > 0 && faved > 0) {
            tvCounter.setText(countRT + " " + countFav);
            tvCounter.setVisibility(View.VISIBLE);
        }
        else if (retweeted > 0) {
            tvCounter.setText(countRT);
            tvCounter.setVisibility(View.VISIBLE);
        }
        else if (faved > 0) {
            tvCounter.setText(countFav);
            tvCounter.setVisibility(View.VISIBLE);
        }

        return v;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        viewConverter = TweetAdapterWrap.ViewConverter.newInstance(
                getActivity(),
                (user != null)? user.toSingleList() : null,
                PreferenceManager.getDefaultSharedPreferences(getActivity()),
                PreformedStatus.class);
        getActivity().bindService(new Intent(getActivity(), TwitterService.class), connection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onResume() {
        super.onResume();

        if (currentDialog != null) {
            currentDialog.show();
        }

        if (status != null) {
            getView().post(new Runnable() {
                @Override
                public void run() {
                    viewConverter.convertView(tweetView, status, TweetAdapterWrap.ViewConverter.MODE_DETAIL);
                }
            });
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (serviceBound) {
            getActivity().unbindService(connection);
            serviceBound = false;
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == REQUEST_REPLY) {
                getActivity().finish();
            }
            else {
                final ArrayList<AuthUserRecord> actionUsers =
                        (ArrayList<AuthUserRecord>) data.getSerializableExtra(AccountChooserActivity.EXTRA_SELECTED_RECORDS);
                if ((requestCode & REQUEST_RETWEET) == REQUEST_RETWEET) {
                    new SimpleAsyncTask() {
                        @Override
                        protected Void doInBackground(Void... params) {
                            for (AuthUserRecord user : actionUsers) {
                                service.retweetStatus(user, (status.isRetweet()) ? status.getRetweetedStatus().getId() : status.getId());
                            }
                            return null;
                        }
                    }.execute();
                }
                if ((requestCode & REQUEST_FAVORITE) == REQUEST_FAVORITE) {
                    new SimpleAsyncTask() {
                        @Override
                        protected Void doInBackground(Void... params) {
                            for (AuthUserRecord user : actionUsers) {
                                service.createFavorite(user, (status.isRetweet()) ? status.getRetweetedStatus().getId() : status.getId());
                            }
                            return null;
                        }
                    }.execute();
                }
            }
        }
    }

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            TwitterService.TweetReceiverBinder binder = (TwitterService.TweetReceiverBinder) service;
            StatusMainFragment.this.service = binder.getService();
            serviceBound = true;

            if (StatusMainFragment.this.service.isMyTweet(status) != null && !status.isRetweet()) {
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
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
        }
    };
}
