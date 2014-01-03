package shibafu.yukari.util;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 和文モールスの変換をするよ。 <br/>
 * なお実行環境の文字コードはUTF-8とする。<br/>
 * Created by Shibafu on 13/08/14.
 */
public class MorseCodec {
    private static final String[] SIGNAL_JP ={
            "・−", "・−・−", "−・・・", "−・−・", "−・・", "・", "・・−・・",
            "・・−・", "−−・", "・・・・", "−・−−・", "・−−−", "−・−", "・−・・",
            "−−", "−・", "−−−", "−−−・", "・−−・", "−−・−", "・−・",
            "・・・", "−", "・・−", "・−・・−", "・・−−", "・−・・・", "・・・−",
            "・−−", "−・・−", "−・−−", "−−・・", "−−−−", "−・−−−", "・−・−−",
            "−−・−−", "−・−・−", "−・−・・", "−・・−−", "−・・・−", "・・−・−", "−−・−・",
            "・−−・・", "−−・・−", "−・・−・", "・−−−・", "−−−・−", "・−・−・",
            "・・", "・・−−・", "・−−・−", "・−・−・−", "−・−−・−", "・−・・−・"
    };
    private static final String[] CHAR_JP = {
            "イ", "ロ", "ハ", "ニ", "ホ", "ヘ", "ト",
            "チ", "リ", "ヌ", "ル", "ヲ", "ワ", "カ",
            "ヨ", "タ", "レ", "ソ", "ツ", "ネ", "ナ",
            "ラ", "ム", "ウ", "ヰ", "ノ", "オ", "ク",
            "ヤ", "マ", "ケ", "フ", "コ", "エ", "テ",
            "ア", "サ", "キ", "ユ", "メ", "ミ", "シ",
            "ヱ", "ヒ", "モ", "セ", "ス", "ン",
            "゛", "゜", "ー", "、", "（", "）"
    };

    private static final char[] COMP_CHAR = {
            'ガ', 'ギ', 'グ', 'ゲ', 'ゴ',
            'ザ', 'ジ', 'ズ', 'ゼ', 'ゾ',
            'ダ', 'ヂ', 'ヅ', 'デ', 'ド',
            'バ', 'ビ', 'ブ', 'ベ', 'ボ',
            'パ', 'ピ', 'プ', 'ペ', 'ポ',
            'ァ', 'ィ', 'ゥ', 'ェ', 'ォ',
            'ッ', 'ャ', 'ュ', 'ョ'
    };
    private static final int[] COMP_SHIFT = {
            -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1,
            -2, -2, -2, -2, -2,
            +1, +1, +1, +1, +1,
            +1, +1, +1, +1
    };

    private final static Pattern SIGNAL_PATTERN = Pattern.compile("(((−|・|－)+) ?)+");

    /**
     * 文字列をモールス信号に変換するよ。<br/>
     * @param text モールス信号に変換したい文字列(ひらがな・カタカナ)
     * @return モールス信号にした文字列
     */
    public static String encode(String text) {
        for (int i = 0x3041; i < 0x3097; i++) {
            text = text.replace((char)i, (char)(i + 0x60));
        }

        text = decomposition(text);

        for (int i = 0; i < SIGNAL_JP.length; i++) {
            text = text.replace(CHAR_JP[i], SIGNAL_JP[i] + " ");
        }

        return text;
    }

    /**
     * モールス信号を文字列に変換するよ。<br/>
     * 変換された部分は《》で囲まれるよ。
     * @param signals モールス信号の文字列
     * @return カタカナに変換したもの
     */
    public static String decode(String signals) {
        Matcher matcher = SIGNAL_PATTERN.matcher(signals);

        ArrayList<String> results = new ArrayList<String>();
        while(matcher.find()) {
            String match = matcher.group();
            match = match.replaceAll("－", "−");
            String[] signal = match.split(" ");
            if (signal.length > 1) {
                String decode = "";

                for (int i = 0; i < signal.length; i++) {
                    for (int j = 0; j < SIGNAL_JP.length; j++) {
                        if (signal[i].equals(SIGNAL_JP[j])) {
                            decode += CHAR_JP[j];
                        }
                    }
                }

                decode = composition(decode);
                results.add("《" + decode + "》");
            }
        }
        for (String s : results) {
            signals = signals.replaceFirst("(((−|・|－)+) ?)+", s);
        }

        return signals;
    }

    private static String decomposition(String text) {
        for (int i = 0; i < COMP_CHAR.length; i++) {
            String replace = "";
            if (COMP_SHIFT[i] < 0) {
                replace = ((COMP_SHIFT[i] == -1)? "゛": "゜");
            }
            text = text.replace(String.valueOf(COMP_CHAR[i]), String.valueOf((char)(COMP_CHAR[i] + COMP_SHIFT[i])) + replace);
        }
        return text;
    }

    private static String composition(String text) {
        for (int i = 0; i < COMP_CHAR.length; i++) {
            String replace = "";
            if (COMP_SHIFT[i] < 0) {
                replace = ((COMP_SHIFT[i] == -1)? "゛": "゜");
            }
            int shift = COMP_SHIFT[i];
            if (shift > 0) shift = 0;
            text = text.replace(String.valueOf((char)(COMP_CHAR[i] + shift)) + replace, String.valueOf(COMP_CHAR[i]));
        }
        return text;
    }
}
