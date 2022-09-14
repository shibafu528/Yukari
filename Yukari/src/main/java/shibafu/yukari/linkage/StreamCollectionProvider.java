package shibafu.yukari.linkage;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import shibafu.yukari.database.AuthUserRecord;
import shibafu.yukari.database.Provider;

public interface StreamCollectionProvider {
    /**
     * 指定のアカウントに対応したストリーミングAPIインスタンスを取得します。
     *
     * @param userRecord 認証情報。
     * @return ストリーミングAPIインスタンス。アカウントが所属するサービスに対応したものが返されます。
     */
    @Nullable
    ProviderStream getProviderStream(@NonNull AuthUserRecord userRecord);

    /**
     * 指定のAPI形式に対応したストリーミングAPIインスタンスを取得します。
     * 対応するAPIインスタンスが定義されていない場合、例外をスローします。
     *
     * @param apiType API形式。{@link Provider} 内の定数を参照。
     * @return ストリーミングAPIインスタンス。
     * @throws UnsupportedOperationException 対応するAPIインスタンスが定義されていない場合にスロー
     */
    @NonNull
    ProviderStream getProviderStream(int apiType);

    /**
     * 全てのストリーミングAPIインスタンスを取得します。
     *
     * @return ストリーミングAPIインスタンス。
     */
    ProviderStream[] getProviderStreams();

    /**
     * ユーザによって有効化されているストリーミングチャンネルを全て起動します。
     */
    default void startStreamChannels() {
        for (ProviderStream stream : getProviderStreams()) {
            if (stream != null) {
                stream.onStart();
            }
        }
    }

    /**
     * 現在接続されているストリーミングチャンネルを一度切断し、接続しなおします。
     */
    default void reconnectStreamChannels() {
        for (ProviderStream stream : getProviderStreams()) {
            if (stream != null) {
                for (StreamChannel channel : stream.getChannels()) {
                    if (channel.isRunning()) {
                        channel.stop();
                        channel.start();
                    }
                }
            }
        }
    }
}
