package shibafu.yukari.linkage;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import shibafu.yukari.database.AuthUserRecord;
import shibafu.yukari.database.Provider;
import shibafu.yukari.linkage.ProviderApi;

public interface ApiCollectionProvider {
    /**
     * 指定のアカウントに対応したAPIインスタンスを取得します。
     *
     * @param userRecord 認証情報。
     * @return APIインスタンス。アカウントが所属するサービスに対応したものが返されます。
     */
    @Nullable
    ProviderApi getProviderApi(@NonNull AuthUserRecord userRecord);

    /**
     * 指定のAPI形式に対応したAPIインスタンスを取得します。
     * 対応するAPIインスタンスが定義されていない場合、例外をスローします。
     *
     * @param apiType API形式。{@link Provider} 内の定数を参照。
     * @return APIインスタンス。
     * @throws UnsupportedOperationException 対応するAPIインスタンスが定義されていない場合にスロー
     */
    @NonNull
    ProviderApi getProviderApi(int apiType);
}
