package shibafu.yukari.fragment;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import shibafu.yukari.R;
import shibafu.yukari.activity.StatusActivity;
import shibafu.yukari.activity.TweetActivity;
import shibafu.yukari.common.TweetAdapterWrap;
import shibafu.yukari.service.TwitterService;
import shibafu.yukari.twitter.AuthUserRecord;
import twitter4j.Status;

/**
 * Created by Shibafu on 13/08/02.
 */
public class StatusMainFragment extends Fragment{

    private Status status = null;
    private AuthUserRecord user = null;

    private TwitterService service;
    private boolean serviceBound = false;

    private AlertDialog currentDialog = null;
    private ImageButton ibFavorite;
    private ImageButton ibFavRt;
    private ImageButton ibRetweet;
    private View tweetView;
    private TextView tvCounter;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_status_main, container, false);
        Bundle b = getArguments();
        status = (Status) b.getSerializable(StatusActivity.EXTRA_STATUS);
        user = (AuthUserRecord) b.getSerializable(StatusActivity.EXTRA_USER);
        tweetView = v.findViewById(R.id.status_tweet);

        ImageButton ibReply = (ImageButton) v.findViewById(R.id.ib_state_reply);
        ibReply.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getActivity(), TweetActivity.class);
                intent.putExtra(TweetActivity.EXTRA_USER, user);
                intent.putExtra(TweetActivity.EXTRA_STATUS, ((status.isRetweet())?status.getRetweetedStatus() : status));
                intent.putExtra(TweetActivity.EXTRA_REPLY, true);
                intent.putExtra(TweetActivity.EXTRA_TEXT, "@" +
                        ((status.isRetweet())?status.getRetweetedStatus().getUser().getScreenName()
                                : status.getUser().getScreenName()) + " ");
                startActivity(intent);
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
                if (source.contains("ShootingStar") || source.contains("TheWorld") || source.contains("Biyon≡(　ε:)"))
                {
                    Pattern VIA_PATTERN = Pattern.compile("<a .*>(.+)</a>");
                    Matcher matcher = VIA_PATTERN.matcher(source);
                    String via;
                    if (matcher.find()) {
                        via = matcher.group(1);
                    }
                    else {
                        via = source;
                    }
                    AlertDialog ad = new AlertDialog.Builder(getActivity())
                            .setTitle("だいじな確認")
                            .setMessage("このツイートのviaは特定クライアント(" + via + ")のものです。お気に入り登録してもよろしいですか？")
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

        ImageButton ibQuote = (ImageButton) v.findViewById(R.id.ib_state_quote);
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
                        new String[] {
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
                                intent.putExtra(TweetActivity.EXTRA_TEXT, TwitterService.createQuotedRT(status));
                                break;
                            case 1:
                                intent.putExtra(TweetActivity.EXTRA_TEXT, TwitterService.createQT(status));
                                break;
                            case 2:
                                intent.putExtra(TweetActivity.EXTRA_TEXT, TwitterService.createQuote(status));
                                break;
                            case 3:
                                intent.putExtra(TweetActivity.EXTRA_TEXT, " " + TwitterService.getTweetURL(status));
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

        ImageButton ibShare = (ImageButton) v.findViewById(R.id.ib_state_share);
        ibShare.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String stot = TwitterService.createSTOT(status);
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("text/plain");
                intent.putExtra(Intent.EXTRA_TEXT, stot);
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
                    TweetAdapterWrap.setStatusToView(getActivity(),
                            tweetView,
                            status,
                            (user != null)?user.toSingleList() : null,
                            TweetAdapterWrap.CONFIG_SHOW_THUMBNAIL);
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

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            TwitterService.TweetReceiverBinder binder = (TwitterService.TweetReceiverBinder) service;
            StatusMainFragment.this.service = binder.getService();
            serviceBound = true;

            if (StatusMainFragment.this.service.isMyTweet(status)) {
                ibFavorite.setEnabled(false);
                ibFavRt.setEnabled(false);
            }
            if (status.getUser().isProtected()) {
                ibRetweet.setEnabled(false);
                ibFavRt.setEnabled(false);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
        }
    };
}
