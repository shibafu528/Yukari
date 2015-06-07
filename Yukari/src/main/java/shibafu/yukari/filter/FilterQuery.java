package shibafu.yukari.filter;

import android.support.annotation.NonNull;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import shibafu.yukari.filter.compiler.QueryCompiler;
import shibafu.yukari.filter.expression.Expression;
import shibafu.yukari.filter.source.All;
import shibafu.yukari.filter.source.FilterSource;
import shibafu.yukari.twitter.AuthUserRecord;
import twitter4j.TwitterResponse;

/**
 * Created by shibafu on 15/06/06.
 */
public class FilterQuery {

    private List<FilterSource> sources;
    private Expression rootExpression;

    /**
     * 抽出ソースとコンパイルされた式を格納するオブジェクトを生成します。<br/>
     * このコンストラクタはクエリコンパイラ以外から呼ばれてはいけません。呼ばれた場合、{@link IllegalAccessError} がスローされます。
     * @param sources 抽出ソース
     * @param rootExpression コンパイルされたクエリ式
     * @throws IllegalAccessError クエリコンパイラ以外からの呼び出し時にスロー
     */
    public FilterQuery(List<FilterSource> sources, Expression rootExpression) {
        if (!QueryCompiler.class.getName().equals(Thread.currentThread().getStackTrace()[3].getClassName())) {
            Log.e("FilterQuery", "Call from: " + Thread.currentThread().getStackTrace()[3].getClassName());
            Log.e("FilterQuery", "Actual: " + QueryCompiler.class.getName());
            throw new IllegalAccessError("フィルタシステム外からのインスタンス生成は許可されていません。");
        }
        if (sources == null) {
            this.sources = new ArrayList<>(1);
            this.sources.add(new All());
        } else {
            this.sources = sources;
        }
        this.rootExpression = rootExpression;
    }

    public List<FilterSource> getSources() {
        return sources;
    }

    /**
     * ツイートやメッセージをコンパイルされたクエリ式で評価します。
     * @param target 評価対象
     * @param userRecords ユーザアカウント (評価時にアカウント変数として使用されます)
     * @return クエリ式の評価結果 (抽出であれば、真となったら表示するのが妥当です)
     */
    public boolean evaluate(@NonNull TwitterResponse target, @NonNull List<AuthUserRecord> userRecords) {
        return rootExpression.evaluate(target, userRecords);
    }
}
