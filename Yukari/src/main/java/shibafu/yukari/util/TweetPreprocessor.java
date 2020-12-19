package shibafu.yukari.util;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.widget.Toast;
import info.shibafu528.yukari.exvoice.MRubyException;
import info.shibafu528.yukari.exvoice.ProcWrapper;
import info.shibafu528.yukari.exvoice.pluggaloid.Plugin;
import org.jetbrains.annotations.NotNull;
import shibafu.yukari.activity.CommandsPrefActivity;
import shibafu.yukari.activity.ConfigActivity;
import shibafu.yukari.activity.MaintenanceActivity;
import shibafu.yukari.activity.TweetActivity;
import shibafu.yukari.common.async.ThrowableTwitterAsyncTask;
import shibafu.yukari.service.TwitterService;
import shibafu.yukari.database.AuthUserRecord;
import twitter4j.Twitter;
import twitter4j.TwitterException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by shibafu on 2016/02/20.
 */
public class TweetPreprocessor {

    private static final Map<String, TweetPreprocessorAction> COMMANDS = new HashMap<>();

    static {
        COMMANDS.put("::cmd", (depends, input) -> {
            depends.getActivity().startActivity(new Intent(depends.getActivity().getApplicationContext(), CommandsPrefActivity.class));
            return null;
        });
        COMMANDS.put("::main", (depends, input) -> {
            depends.getActivity().startActivity(new Intent(depends.getActivity().getApplicationContext(), MaintenanceActivity.class));
            return null;
        });
        COMMANDS.put("::sb", (depends, input) -> "エビビーム！ﾋﾞﾋﾞﾋﾞﾋﾞﾋﾞﾋﾞﾋﾞｗｗｗｗｗｗ");
        COMMANDS.put("::jb", (depends, input) -> "Javaビームﾋﾞﾋﾞﾋﾞﾋﾞﾋﾞﾋﾞﾋﾞwwwwwwwwww");
        COMMANDS.put("::bb", (depends, input) -> "@la0c bbop " + input);
        COMMANDS.put("::cn", (depends, input) -> {
            if (TextUtils.isEmpty(input)) {
                Toast.makeText(depends.getActivity().getApplicationContext(), "Invalid Input", Toast.LENGTH_SHORT).show();
            } else {
                new ThrowableTwitterAsyncTask<String, Void>() {
                    @Override
                    protected ThrowableResult<Void> doInBackground(String... params) {
                        try {
                            for (AuthUserRecord user : depends.getWriters()) {
                                Twitter twitter = depends.getActivity().getTwitterService().getTwitterOrThrow(user);
                                twitter.updateProfile(params[0], null, null, null);
                            }
                            return new ThrowableResult<>((Void) null);
                        } catch (TwitterException e) {
                            e.printStackTrace();
                            return new ThrowableResult<>(e);
                        }
                    }

                    @Override
                    protected void onPreExecute() {
                        super.onPreExecute();
                        showToast("Updating your name...");
                    }

                    @Override
                    protected void onPostExecute(ThrowableResult<Void> result) {
                        super.onPostExecute(result);
                        if (!result.isException()) {
                            showToast("Updated your name!");
                        }
                    }

                    @Override
                    protected void showToast(String message) {
                        Toast.makeText(depends.getActivity().getApplicationContext(), message, Toast.LENGTH_LONG).show();
                    }
                }.execute(input);
                depends.getActivity().setResult(Activity.RESULT_OK);
                depends.getActivity().finish();
            }
            return null;
        });
        COMMANDS.put("::d250g2", (depends, input) -> {
            if (TextUtils.isEmpty(input)) {
                return "http://twitpic.com/d250g2";
            } else {
                return input + " http://twitpic.com/d250g2";
            }
        });
        COMMANDS.put("::grgr", (depends, input) -> "三('ω')三( ε: )三(.ω.)三( :3 )三('ω')三( ε: )三(.ω.)三( :3 )三('ω')三( ε: )三(.ω.)三( :3 )ゴロゴロゴロ");
        COMMANDS.put("::sy", (depends, input) -> "( ˘ω˘)ｽﾔｧ…");
        COMMANDS.put("::balus", (depends, input) -> {
            Activity activity = depends.getActivity();
            activity.sendBroadcast(new Intent("shibafu.yukari.BALUS"));
            activity.setResult(Activity.RESULT_OK);
            activity.finish();
            return null;
        });
        COMMANDS.put("::batt", (depends, input) -> depends.getBatteryTweetText());
        COMMANDS.put("::ay", (depends, input) -> "#あひる焼き");
        COMMANDS.put("::mh", (depends, input) -> {
            // Quote from  https://github.com/0V/MohyoButton/blob/master/MohyoButton/Models/MohyoTweet.cs
            // MIT License https://github.com/0V/MohyoButton/blob/master/LICENSE
            final String[] MOHYO =  {
                    "もひょ",
                    "もひょっ",
                    "もひょぉ",
                    "もひょもひょ",
                    "もひょもひょっ",
                    "もひょもひょぉ",
                    "もひょもひょもひょもひょ",
                    "＞ω＜もひょ",
                    "(~´ω`)~もひょ",
                    "~(´ω`~)もひょ",
                    "(～＞ω＜)～もひょ",
                    "～(＞ω＜～)もひょ",
                    "～(＞ω＜)～もひょ",
                    "進捗もひょです",
                    "Mohyo",
                    "mohyo",
                    "むいっ",
            };
            // End of quote
            return MOHYO[new Random().nextInt(MOHYO.length)];
        });
        COMMANDS.put("::ma", (depends, input) -> {
            depends.getActivity().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://wiki.famitsu.com/kairi/"))
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            depends.getActivity().setResult(Activity.RESULT_OK);
            depends.getActivity().finish();
            return null;
        });
        COMMANDS.put("::meu", (depends, input) -> "めめめめめめめ めうめうーっ！(」*ﾟﾛﾟ)」めめめ めうめうーっ！(」*ﾟﾛﾟ)」*ﾟﾛﾟ)」 ぺーったんぺったんぺったんぺったん 大好き～っ☆⌒ヽ(*'､＾*)");
        COMMANDS.put("::dice", (depends, input) -> {
            if (TextUtils.isEmpty(input)) {
                Pattern pattern = Pattern.compile("(\\d+).(\\d+)");
                Matcher m = pattern.matcher(input);
                if (m.find() && m.groupCount() == 2) {
                    int randomSum = 0;
                    Random r = new Random();
                    int count = Integer.parseInt(m.group(1));
                    int length = Integer.parseInt(m.group(2));
                    if (count < 1 || length < 1) {
                        Toast.makeText(depends.getActivity().getApplicationContext(), "Invalid Input", Toast.LENGTH_SHORT).show();
                        return null;
                    }
                    for (int i = 0; i < count; i++) {
                        randomSum += r.nextInt(length) + 1;
                    }
                    return String.format("%dd%d => [%d]", count, length, randomSum);
                } else {
                    Toast.makeText(depends.getActivity().getApplicationContext(), "Invalid Input", Toast.LENGTH_SHORT).show();
                    return null;
                }
            } else {
                final String[] dice = {"⚀", "⚁", "⚂", "⚃", "⚄", "⚅"};
                Random r = new Random();
                return dice[r.nextInt(6)];
            }
        });
        COMMANDS.put("::yk", (depends, input) -> {
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(depends.getActivity());
            long count = sp.getLong("count_yk", 1);
            sp.edit().putLong("count_yk", count + 1).apply();
            return String.format("ゆかりさんゆかりさん！！(%d回目)", count);
        });
        COMMANDS.put("::rev", (depends, input) -> {
            if (TextUtils.isEmpty(input)) {
                Toast.makeText(depends.getActivity().getApplicationContext(), "Input is empty", Toast.LENGTH_SHORT).show();
                return null;
            }

            StringBuilder sb = new StringBuilder();
            for (int i = input.length() - 1; 0 <= i; i--) {
                sb.append(input.charAt(i));
            }
            return sb.toString();
        });
        COMMANDS.put("::shuf", (depends, input) -> {
            if (TextUtils.isEmpty(input)) {
                Toast.makeText(depends.getActivity().getApplicationContext(), "Input is empty", Toast.LENGTH_SHORT).show();
                return null;
            }

            List<Character> list = new ArrayList<>();
            for (int i = 0; i < input.length(); i++) {
                list.add(input.charAt(i));
            }
            Collections.shuffle(list);
            StringBuilder sb = new StringBuilder();
            for (Character c : list) {
                sb.append(c);
            }
            return sb.toString();
        });
        COMMANDS.put("::conf", (depends, input) -> {
            depends.getActivity().startActivity(new Intent(depends.getActivity().getApplicationContext(), ConfigActivity.class));
            depends.getActivity().setResult(Activity.RESULT_OK);
            depends.getActivity().finish();
            return null;
        });
        COMMANDS.put("::te", (depends, input) -> {
            Toast.makeText(depends.getActivity().getApplicationContext(), "::te は廃止されました", Toast.LENGTH_SHORT).show();
            return null;
        });
        COMMANDS.put("::td", (depends, input) -> {
            Toast.makeText(depends.getActivity().getApplicationContext(), "::td は廃止されました", Toast.LENGTH_SHORT).show();
            return null;
        });
    }

    /**
     * 入力されたツイートのプリプロセスを行います。
     * @param depends 依存オブジェクト
     * @param input ユーザ入力
     * @return コマンドを含んでいる場合、それによって変換された入力。処理の結果、ツイートを中断する場合は null を返します。
     */
    @Nullable
    public static String preprocess(@NotNull TweetPreprocessorDepends depends, @Nullable String input) {
        if (input != null && input.startsWith("::")) {
            String command = input.split(" ")[0];
            // プラグインからマッチング
            if (depends.getActivity().isTwitterServiceBound()) {
                TwitterService service = depends.getActivity().getTwitterService();
                if (service != null && service.getmRuby() != null) {
                    // プラグインからコマンドを取得
                    try {
                        Object[] result = Plugin.filtering(service.getmRuby(), "post_command", new LinkedHashMap());
                        if (result != null && result[0] instanceof Map) {
                            Map commands = (Map) result[0];

                            // マッチするコマンドがあればProcを実行
                            Object proc = commands.get(command.replaceFirst("^::", ""));
                            try {
                                if (proc != null && proc instanceof ProcWrapper) {
                                    return (String) ((ProcWrapper) proc).exec(input.replace(command, "").trim());
                                }
                            } catch (MRubyException e) {
                                e.printStackTrace();
                                Toast.makeText(depends.getActivity().getApplicationContext(),
                                        String.format("Procの実行中にMRuby上で例外が発生しました\n%s", e.getMessage()),
                                        Toast.LENGTH_LONG).show();
                                return input;
                            } finally {
                                for (Object procWrapper : commands.values()) {
                                    if (procWrapper instanceof ProcWrapper) {
                                        ((ProcWrapper) procWrapper).dispose();
                                    }
                                }
                            }
                        }
                    } catch (MRubyException e) {
                        e.printStackTrace();
                        Toast.makeText(depends.getActivity().getApplicationContext(),
                                String.format("プラグインの呼び出し中にMRuby上で例外が発生しました\n%s", e.getMessage()),
                                Toast.LENGTH_LONG).show();
                        return input;
                    }
                }
            }
            // ビルトインプリプロセッサからマッチング
            TweetPreprocessorAction action = COMMANDS.get(command);
            if (action != null) {
                return action.preprocess(depends, input.replace(command, "").trim());
            }
        }
        return input;
    }

    /**
     * プリプロセスの依存オブジェクト
     */
    public interface TweetPreprocessorDepends {
        TweetActivity getActivity();
        List<AuthUserRecord> getWriters();
        String getBatteryTweetText();
    }

    /**
     * プリプロセスのアクション
     */
    private interface TweetPreprocessorAction {
        /**
         * ツイートのプリプロセスを実行します。
         * @param depends 依存オブジェクト
         * @param input ユーザ入力
         * @return 変換した入力。ツイートを中断する場合は null 。
         */
        @Nullable String preprocess(final TweetPreprocessorDepends depends, final @NotNull String input);
    }
}
