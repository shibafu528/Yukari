package shibafu.yukari.export

import com.google.gson.Gson
import com.google.gson.internal.UnsafeAllocator
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonWriter
import shibafu.yukari.twitter.AuthUserRecord
import java.io.StringWriter
import java.math.BigDecimal

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

    private val migrator: Map<Int, (MutableMap<String, Any?>) -> Unit> = MigratorBuilder().let { it.setup(); it.migrator }

    /**
     * 設定データを最新のバージョンにマイグレーションしてから、POJOに変換します。
     * @param config 設定データ
     * @param version 設定データのバージョン
     * @return 設定データのPOJO
     */
    fun toLatestDataObject(config: MutableMap<String, Any?>, version: Int): T {
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

        // POJOへの変換
        val result = UnsafeAllocator.create().newInstance(clazz)
        config.forEach { entry ->
            try {
                val field = clazz.getDeclaredField(entry.key)
                field.isAccessible = true

                if (field.type.isPrimitive) {
                    val decimal = lazy { BigDecimal(entry.value.toString()) }
                    when (field.type.name) {
                        "int" -> field.setInt(result, decimal.value.toInt())
                        "long" -> field.setLong(result, decimal.value.toLong())
                        "short" -> field.setShort(result, decimal.value.toShort())
                        "float" -> field.setFloat(result, decimal.value.toFloat())
                        "double" -> field.setDouble(result, decimal.value.toDouble())
                        "char" -> field.setChar(result, entry.value.toString().first())
                        "byte" -> field.setByte(result, decimal.value.toByte())
                    }
                } else {
                    field.set(result, entry.value)
                }
            } catch (ignored: NoSuchFieldException) {
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return result
    }

    protected class MigratorBuilder {
        val migrator: MutableMap<Int, (MutableMap<String, Any?>) -> Unit> = hashMapOf()

        /**
         * 指定のバージョンのコンフィグJSONに対するマイグレーション処理を定義します。
         * @param version 処理対象のバージョン
         * @param migrator マイグレーション処理
         */
        fun version(version: Int, migrator: (MutableMap<String, Any?>) -> Unit) {
            this.migrator[version] = migrator
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
            AuthUserRecord::class.java to AccountMigrator()
    )

    /**
     * 設定データをJSON形式でシリアライズします。
     * @param clazz 設定データの型
     * @param items 設定データ
     * @return JSON
     */
    fun <T> exportToJson(clazz: Class<T>, items: List<T>): String {
        val filter = filters[clazz] ?: return ""
        val writer = StringWriter()
        JsonWriter(writer).use { json ->
            json.beginObject()

            json.name("version")
            json.value(filter.latestVersion)

            json.name(clazz.simpleName)
            Gson().toJson(items, object : TypeToken<List<T>>() {}.type, json)

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
    fun <T> importFromJson(clazz: Class<T>, json: String): List<T> {
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
        val contents = decodedJson[clazz.simpleName] ?: throw InvalidJsonException("invalid json : not contains key '${clazz.simpleName}'")

        @Suppress("UNCHECKED_CAST")
        if (contents is Map<*, *>) {
            return listOf(filter.toLatestDataObject(contents as MutableMap<String, Any?>, version))
        } else if (contents is Collection<*>) {
            return contents.map { filter.toLatestDataObject(it as MutableMap<String, Any?>, version) }
        }

        throw InvalidJsonException("invalid json : invalid format key '${clazz.simpleName}'")
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