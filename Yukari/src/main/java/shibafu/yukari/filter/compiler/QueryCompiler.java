package shibafu.yukari.filter.compiler;

import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import shibafu.yukari.filter.FilterQuery;
import shibafu.yukari.filter.expression.ConstantValue;
import shibafu.yukari.filter.expression.Expression;
import shibafu.yukari.filter.source.All;
import shibafu.yukari.filter.source.FilterSource;
import shibafu.yukari.twitter.AuthUserRecord;

/**
 * Created by shibafu on 15/06/06.
 */
public final class QueryCompiler {
    public static final String DEFAULT_QUERY = "from all";
    private static final String LOG_TAG = "QueryCompiler";

    /**
     * クエリ文字列を解釈し、ソースリストと式オブジェクトにコンパイルします。
     * @param userRecords ソースリストに関連付けるユーザのリスト
     * @param query クエリ文字列
     * @return コンパイル済クエリ
     */
    public static FilterQuery compile(List<AuthUserRecord> userRecords, String query) {
        //コンパイル開始時間の記録
        long compileTime = System.currentTimeMillis();

        //userRecords: null -> 0件のユーザリスト
        if (userRecords == null) {
            userRecords = new ArrayList<>();
        }
        //query: null or empty -> 全抽出のクエリということにする
        if (TextUtils.isEmpty(query)) {
            query = DEFAULT_QUERY;
        }

        //from句とwhere句の開始位置と存在チェック
        int beginFrom = query.indexOf("from");
        int beginWhere = query.indexOf("where");

        //from句の解釈
        List<FilterSource> sources;
        if (beginFrom < 0) {
            //from句が存在しない -> from allと同義とする
            sources = new ArrayList<>(1);
            sources.add(new All());
        } else {
            sources = parseSource(beginWhere < 0 ? query : query.substring(0, beginWhere - 1));
        }

        //where句の解釈
        Expression rootExpression;
        if (beginWhere < 0) {
            //where句が存在しない -> where trueと同義とする
            rootExpression = new ConstantValue(true);
        } else {
            rootExpression = parseExpression(query.substring(beginWhere));
        }

        //コンパイル終了時間の記録
        compileTime = System.currentTimeMillis() - compileTime;
        Log.d(LOG_TAG, String.format("Compile finished. (%dms): %s", compileTime, query));

        //コンパイル結果を格納
        return new FilterQuery(sources, rootExpression);
    }

    @NonNull
    private static List<FilterSource> parseSource(@NonNull String fromQuery) {
        return new ArrayList<FilterSource>(1){{add(new All());}};
    }

    @NonNull
    private static Expression parseExpression(@NonNull String whereQuery) {
        return new ConstantValue(true);
    }
}
