package shibafu.yukari.export

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonWriter
import shibafu.yukari.common.TabInfo
import shibafu.yukari.database.AutoMuteConfig
import shibafu.yukari.database.Bookmark
import shibafu.yukari.database.MuteConfig
import shibafu.yukari.database.Provider
import shibafu.yukari.database.SearchHistory
import shibafu.yukari.database.StreamChannelState
import shibafu.yukari.database.UserExtras
import shibafu.yukari.entity.StatusDraft
import shibafu.yukari.twitter.AuthUserRecord
import java.io.StringWriter

/**
 * 設定データのマイグレーション処理を定義し、その機能を提供します。
 * @param clazz 設定データの型
 * @param setup マイグレーション処理の定義を行う関数
 */
abstract class ConfigFileMigrator<out T> protected constructor (private val clazz: Class<T>, setup: MigratorBuilder.() -> Unit) {
    /**
     * 現在の最新バージョン番号。
     */
    abstract val latestVersion: Int

    private val migrator: Map<Int, (MutableMap<String, Any?>) -> Unit>

    private val oldNames: Map<IntRange, String>

    init {
        val builder = MigratorBuilder().apply(setup)

        migrator = builder.migrator
        oldNames = builder.oldNames
    }

    /**
     * 設定データを最新のバージョンにマイグレーションします。
     * @param config 設定データ
     * @param version 設定データのバージョン
     * @return 設定データ
     */
    fun toLatestDataObject(config: MutableMap<String, Any?>, version: Int): MutableMap<String, Any?> {
        // バージョン番号チェック
        if (version > latestVersion) {
            throw IncompatibleConfigVersionException("未対応バージョンのデータが指定されています。")
        } else if (version < 0) {
            throw IncompatibleConfigVersionException("不正なデータが指定されています。")
        }

        // マイグレーションを実行
        if (version < latestVersion) {
            for (i in version + 1..latestVersion) {
                migrator[i]?.invoke(config)
            }
        }

        return config
    }

    /**
     * 設定データの型に対応するキー (クラス名) を取得します。
     * @param version 設定データのバージョン
     * @return キー
     */
    fun getClassName(version: Int): String {
        oldNames.forEach { (range, name) -> if (range.contains(version)) return name }
        return clazz.simpleName
    }

    protected class MigratorBuilder {
        val migrator: MutableMap<Int, (MutableMap<String, Any?>) -> Unit> = hashMapOf()
        val oldNames: MutableMap<IntRange, String> = hashMapOf()

        /**
         * 指定のバージョンのコンフィグJSONに対するマイグレーション処理を定義します。
         * @param version 処理対象のバージョン
         * @param migrator マイグレーション処理
         */
        fun migrateTo(version: Int, migrator: (MutableMap<String, Any?>) -> Unit) {
            this.migrator[version] = migrator
        }

        /**
         * 過去に使用されていたクラス名を定義し、別名として読み込み可能にします。
         * @param version 古いクラス名が使用されていたバージョン範囲
         * @param oldClassName 古いクラス名
         */
        fun oldName(version: IntRange, oldClassName: String) {
            this.oldNames[version] = oldClassName
        }
    }
}

/**
 * 設定データとJSONの相互変換、およびインポートの際のマイグレーション処理を提供します。
 */
object ConfigFileUtility {
    /**
     * データクラスとマイグレータの対応マップ
     */
    private var filters: Map<Class<*>, ConfigFileMigrator<*>> = mapOf(
            AuthUserRecord::class.java to AccountMigrator(),
            Provider::class.java to ProviderMigrator(),
            TabInfo::class.java to TabInfoMigrator(),
            MuteConfig::class.java to MuteConfigMigrator(),
            AutoMuteConfig::class.java to AutoMuteConfigMigrator(),
            UserExtras::class.java to UserExtrasMigrator(),
            Bookmark.SerializeEntity::class.java to BookmarkMigrator(),
            StatusDraft::class.java to StatusDraftMigrator(),
            SearchHistory::class.java to SearchHistoryMigrator(),
            StreamChannelState::class.java to StreamChannelStateMigrator()
    )

    /**
     * 設定データをJSON形式でシリアライズします。
     * @param clazz 設定データの型
     * @param items 設定データ
     * @return JSON
     */
    fun <T> exportToJson(clazz: Class<T>, items: List<Map<String, Any?>>): String {
        val filter = filters[clazz] ?: return ""
        val writer = StringWriter()
        JsonWriter(writer).use { json ->
            json.beginObject()

            json.name("version")
            json.value(filter.latestVersion)

            json.name(clazz.simpleName)
            Gson().toJson(items, object : TypeToken<List<Map<String, Any?>>>() {}.type, json)

            json.endObject()
        }

        return writer.toString()
    }

    /**
     * JSONを読み込み、設定データのインスタンスとしてデシリアライズします。
     * 古いバージョンのJSONは自動的にマイグレーションを行います。
     * @param clazz 設定データの型
     * @param json JSON
     * @return 設定データ
     * @throws IllegalArgumentException 型に対応するマイグレータが登録されていない場合にスローされます。
     * @throws InvalidJsonException JSONに必要な情報が含まれていない場合など、不適切なJSONであった場合にスローされます。
     */
    @Throws(InvalidJsonException::class)
    fun <T> importFromJson(clazz: Class<T>, json: String): List<MutableMap<String, Any?>> {
        @Suppress("UNCHECKED_CAST")
        val filter = filters[clazz] as? ConfigFileMigrator<T> ?: throw IllegalArgumentException("invalid argument 'clazz' : $clazz")

        val decodedJson = Gson().fromJson(json, Map::class.java)
        if (!decodedJson.containsKey("version")) {
            throw InvalidJsonException("invalid json : not contains key 'version'")
        }

        val version = decodedJson["version"].let {
            when (it) {
                is Int -> it
                is Double -> it.toInt()
                else -> java.lang.Integer.valueOf(it.toString())
            }
        }
        val className = filter.getClassName(version)

        val contents = decodedJson[className] ?: throw InvalidJsonException("invalid json : not contains key '$className'")

        @Suppress("UNCHECKED_CAST")
        if (contents is Map<*, *>) {
            return listOf(filter.toLatestDataObject(contents as MutableMap<String, Any?>, version))
        } else if (contents is Collection<*>) {
            return contents.map { filter.toLatestDataObject(it as MutableMap<String, Any?>, version) }
        }

        throw InvalidJsonException("invalid json : invalid format key '$className'")
    }
}

/**
 * 互換性のないコンフィグデータを検出した場合にスローされます。
 */
class IncompatibleConfigVersionException(message: String) : Exception(message)

/**
 * 設定データとしての必須情報が欠落したJSONを処理しようとした場合、それを中断してスローされます。
 */
class InvalidJsonException(message: String) : Exception(message)